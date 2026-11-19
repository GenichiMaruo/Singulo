"""Electric centrifugal pump. Coordinates and UVs are in sixteenths of a block."""
from PIL import Image, ImageDraw

FACES = ('down', 'up', 'north', 'south', 'west', 'east')
COLORS = {'iron': (53, 63, 67), 'case': (39, 104, 107), 'edge': (81, 146, 143),
          'bronze': (181, 107, 62), 'brass': (222, 166, 86), 'steel': (137, 155, 156),
          'rubber': (38, 42, 48), 'dial': (228, 223, 186)}


def material(key):
    image = Image.new('RGB', (16, 16))
    base = COLORS[key]
    for y in range(16):
        for x in range(16):
            grain = ((13*x + 19*y + 7*x*y) % 9) - 4
            brush = 4 if key in ('steel', 'brass', 'bronze') and y % 4 == 1 else 0
            image.putpixel((x, y), tuple(c + grain + brush for c in base))
    if key == 'dial':
        # Front face of the pressure gauge uses UV [0,0,5,5] at native scale.
        d = ImageDraw.Draw(image)
        for p in ((0, 2), (1, 1), (3, 1), (4, 2)):
            d.point(p, fill=(79, 79, 66))
        d.line((2, 3, 3, 2), fill=(164, 48, 33))
        d.point((2, 3), fill=(50, 52, 51))
    return image


def box_uv(side, a, b):
    x, y, z = a
    X, Y, Z = b
    return {'down': [x, 16-Z, X, 16-z], 'up': [x, z, X, Z],
            'north': [16-X, 16-Y, 16-x, 16-y], 'south': [x, 16-Y, X, 16-y],
            'west': [z, 16-Y, Z, 16-y], 'east': [16-Z, 16-Y, 16-z, 16-y]}[side]


def geometry():
    parts = []

    def box(a, b, key, front=None):
        x, y, z = a
        X, Y, Z = b
        # Minecraft box UV projection. Cropping a 16px tile keeps one texel per model unit.
        faces = {side: {'uv': box_uv(side, a, b), 'texture': '#'+key} for side in FACES}
        if front:
            faces['north'] = {'uv': [0, 0, X-x, Y-y], 'texture': '#'+front}
        parts.append({'from': list(a), 'to': list(b), 'faces': faces})

    def drum(z0, z1, outer, key, hole=0):
        # Merge identical horizontal runs of the stepped castings; the inlet is physically hollow.
        runs = {}
        for y in range(16):
            row = [x for x in range(16) if hole**2 <= (x-7.5)**2 + (y-7.5)**2 < outer**2]
            for x in row:
                if x-1 in row:
                    continue
                end = x+1
                while end in row:
                    end += 1
                runs.setdefault((x,end), []).append(y)
        for (x, end), ys in runs.items():
            start = last = ys[0]
            for y in ys[1:]+[99]:
                if y != last+1:
                    box((x,start,z0),(end,last+1,z1),key)
                    start = y
                last = y

    # Split volute casing, axial suction flange, impeller vanes visible inside the throat.
    drum(3, 7, 6, 'case')
    drum(4, 5, 6.5, 'edge')
    drum(1, 3, 4.5, 'bronze', 3)
    drum(0, 1, 5.5, 'bronze', 3)
    for x in (4,11):
        for y in (5,10):
            box((x,y,0),(x+1,y+1,1),'brass')
    box((5,5,2),(11,11,3),'rubber')
    box((7,7,1),(9,9,3),'brass')
    for a,b in [((8,9,2),(10,10,3)),((9,6,2),(10,8,3)),((6,6,2),(8,7,3)),((6,8,2),(7,10,3))]:
        box(a,b,'steel')

    # Coupling, ribbed electric motor and end bell.
    drum(7, 9, 3.5, 'brass')
    drum(9, 14, 4.5, 'case')
    for z in (9,11,13):
        drum(z, z+1, 5.5, 'edge')
    drum(14, 15, 4, 'iron')

    # Five radial/discharge connections. The darker neck and light machined lip mark the outlets.
    for axis, sign in ((0,-1),(0,1),(1,-1),(1,1),(2,1)):
        for lo, hi, half, mat in ((10,14,2,'bronze'),(14,15,3,'rubber')):
            a,b = [8-half]*3,[8+half]*3
            a[axis],b[axis] = (lo,hi) if sign==1 else (16-hi,16-lo)
            box(a,b,mat)
        # A recessed bore and four steel edges form the open end of each outlet.
        capface = {0: ('west','east'), 1: ('down','up'), 2: ('north','south')}[axis][sign==1]
        parts[-1]['faces'][capface]['texture'] = '#socket'
        u, v = [i for i in range(3) if i != axis]
        for u0,u1,v0,v1 in ((5,11,5,6),(5,11,10,11),(5,6,6,10),(10,11,6,10)):
            a, b = [0]*3, [0]*3
            a[axis], b[axis] = (15,16) if sign==1 else (0,1)
            a[u], b[u], a[v], b[v] = u0,u1,v0,v1
            box(a,b,'steel')

    # Service feet, terminal box, and a separate brass pressure gauge on a bent stem.
    for x in (2,11):
        box((x,0,3),(x+3,2,14),'iron')
        for z in (4,11):
            box((x+1,2,z),(x+2,5,z+2),'steel')
    box((3,11,10),(8,14,13),'iron')
    # Leave the full 6 x 6 footprint of the upper socket free at y >= 14.
    box((3,14,10),(5,15,11),'bronze')
    box((3,14,11),(8,15,13),'bronze')
    box((10,10,5),(12,13,7),'brass')
    box((9,11,4),(16,14,7),'bronze')
    box((9,14,4),(16,16,5),'bronze')
    box((11,14,5),(16,16,7),'bronze')
    box((10,11,3),(15,16,4),'steel','dial')
    return parts


def exposed_faces(parts):
    """Bake the castings as one shell without buried faces or coplanar motor fins."""
    from itertools import product
    cells = {}
    for part in parts:
        for cell in product(*(range(lo, hi) for lo, hi in zip(part['from'], part['to']))):
            cells[cell] = part
    result = []
    for side, axis, step in [('west',0,-1),('east',0,1),('down',1,-1),('up',1,1),('north',2,-1),('south',2,1)]:
        u, v = [i for i in range(3) if i != axis]
        layers = {}
        for cell, part in cells.items():
            neighbour = list(cell)
            neighbour[axis] += step
            if tuple(neighbour) in cells:
                continue
            face = part['faces'][side]
            uv = box_uv(side, part['from'], part['to'])
            key = face['texture'], face['uv'][0] - uv[0], face['uv'][1] - uv[1]
            layers.setdefault((cell[axis], key), set()).add((cell[u], cell[v]))
        for (depth, (texture, du, dv)), pixels in layers.items():
            while pixels:
                x, y = min(pixels)
                end_x, end_y = x + 1, y + 1
                while (end_x, y) in pixels:
                    end_x += 1
                while all((i, end_y) in pixels for i in range(x, end_x)):
                    end_y += 1
                pixels.difference_update(product(range(x, end_x), range(y, end_y)))
                a, b = [0]*3, [0]*3
                a[axis], b[axis] = depth, depth + 1
                a[u], b[u], a[v], b[v] = x, end_x, y, end_y
                uv = box_uv(side, a, b)
                face = {'texture': texture, 'uv': [uv[0]+du, uv[1]+dv, uv[2]+du, uv[3]+dv]}
                if depth == (0 if step < 0 else 15):
                    face['cullface'] = side
                result.append({'from': a, 'to': b, 'faces': {side: face}})
    return result


def generate(ctx):
    assets = ctx['ASSETS']
    write = ctx['write_json']
    folder = assets/'textures/block'
    folder.mkdir(parents=True, exist_ok=True)
    images = {key: material(key) for key in COLORS}
    socket = images['steel'].copy()
    pen = ImageDraw.Draw(socket)
    pen.rectangle((6,6,9,9), fill=(52,68,69))
    pen.rectangle((7,7,8,8), fill=(24,38,41))
    images['socket'] = socket
    for key,image in images.items():
        image.save(folder/f'fluid_pump_{key}.png')

    elements = geometry()
    textures = {k: 'singulo:block/fluid_pump_'+k for k in images}
    textures['particle'] = textures['case']
    write(assets/'models/block/fluid_pump.json', {'parent': 'minecraft:block/block', 'textures': textures, 'elements': exposed_faces(elements)})
    write(assets/'models/item/fluid_pump.json', {'parent': 'singulo:block/fluid_pump'})
    variants = {}
    for face,x,y in [('north',0,0),('east',0,90),('south',0,180),('west',0,270),('down',90,0),('up',270,0)]:
        variant = {'model': 'singulo:block/fluid_pump'}
        if x: variant['x'] = x
        if y: variant['y'] = y
        variants['facing='+face] = variant
    write(assets/'blockstates/fluid_pump.json', {'variants': variants})

    # Front projection for the recipe index, using the actual block faces and UVs.
    icon = Image.new('RGBA',(16,16))
    for part in sorted(elements,key=lambda e:-e['from'][2]):
        x,y,_ = part['from']
        X,Y,_ = part['to']
        face = part['faces']['north']
        tile = images[face['texture'][1:]].crop(tuple(face['uv'])).transpose(Image.Transpose.FLIP_LEFT_RIGHT)
        icon.paste(tile,(16-X,16-Y))
    icon.save(folder/'fluid_pump_front.png')
