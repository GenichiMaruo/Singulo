# -*- coding: utf-8 -*-
"""0.24.0: アンドロイドのプログラム（Python に似た書き方で、アンドロイドの動きを書く）。

プログラムカードの ID・レシピ・説明文と、書く画面・ステーション・誤りの文言。
言語は Java の android.program（Compiler・Vm・Library）、アンドロイドの動きは android.AndroidProgram。
"""

IDS = {
    'プログラムカード': ('program_card', 'Program Card', 'tool'),
}


def recipes(R, shaped):
    # 手順カードに基礎回路の演算部を載せ、両脇にレッドストーンの配線
    R('プログラムカード', 2, '作業台', {'手順カード': 1, '基礎回路': 1, 'レッドストーン': 2}, cat='tool',
      note='Python に似た書き方で、アンドロイドの動きを書く')
    shaped['プログラムカード'] = ((' C ', 'RPR'), {'C': '基礎回路', 'R': 'レッドストーン', 'P': '手順カード'})


DESC = {
    'プログラムカード': ('手に持って使うと、Python に似た書き方でアンドロイドの動きを書ける。アンドロイド組立ステーションのプログラムの欄に入れ、動き方を「コード」にすると、受け持つアンドロイドがその通りに動く。場所は巡回路の欄の作業位置カード（金床で付けた名前か p1〜p8）。',
                   'Use it in hand to write what an android does, in a Python-like language. Put it in the program slot of an Android Assembly Station and set the mode to Code, and the androids of that station follow it. Places come from the Work Location Cards in the route slots (named with an anvil, or p1 to p8).'),
}

SUMMARY = {
    'プログラムカード': ('アンドロイドの動きを、プログラムで書く。', 'Programs what androids do.'),
}

# 関数の説明（書く画面の一覧に重ねると出る）
FUNCTIONS = {
    'go': ('場所へ歩いて行く。着いたら True、たどり着けなければ False', 'Walk to a place. True on arrival, False if unreachable'),
    'take': ('入れ物から持ち物へ取る（物・数は省ける）。取った数', 'Take items from a container (item and count optional). Returns how many'),
    'put': ('持ち物を入れ物へ入れる（物・数は省ける）。入れた数', 'Put held items into a container (item and count optional). Returns how many'),
    'use': ('ブロックを使う（レバー・ボタン・ドアなど）', 'Use a block (lever, button, door...)'),
    'lever': ('レバーを on（True で入・False で切）にする。もうその向きなら歩いて行かない', 'Set a lever on (True) or off (False). Does not walk there if it already is'),
    'mine': ('ブロックを1つ掘る（採掘アップグレードが要る）。掘れたら True', 'Mine one block (needs a Mining Upgrade). True if mined'),
    'wait': ('秒数だけ待つ', 'Wait this many seconds'),
    'home': ('ステーションへ戻る', 'Walk back to the station'),
    'attack': ('モブと戦う。倒れるか、場所 p から r ブロックの外へ出るまで続ける（p・r は省ける）。倒したら True',
               'Fight a mob until it dies or leaves r blocks around p (p and r optional). True if it was defeated'),
    'auto_fight': ('False にすると、自分で敵を探す・打たれて仕返しするのをやめ、attack の相手とだけ戦う',
                   'False stops seeking enemies and fighting back on its own; it only fights attack targets'),
    'mobs': ('場所 p から r ブロック（省くと5、16まで）のモブの一覧（近い順）。filter で絞れる（"hostile"・"zombie"・"#minecraft:undead"・"!creeper"、そのリスト）',
             'Mobs within r blocks of p (5 if omitted, up to 16), nearest first. filter narrows them ("hostile", "zombie", "#minecraft:undead", "!creeper", or a list)'),
    'matches': ('モブが絞り込み（mobs と同じ書き方）に合えば True', 'True if the mob fits the filter (written as for mobs)'),
    'distance': ('2つの場所かモブの間の距離', 'Distance between two places or mobs'),
    'send': ('r ブロック（省くと16、64まで）以内の仲間のアンドロイド（同じ持ち主か登録者の、コードで動くもの）へ知らせを送る。届いた数',
             'Send a message to teammate androids running code within r blocks (16 if omitted, up to 64; same owner or members). Returns how many got it'),
    'receive': ('届いた知らせを1つ取り出す（topic を省けば何でも）。なければ None。.topic・.data・.pos・.sender を読める',
                'Take one received message (any topic if omitted), or None. Read .topic, .data, .pos and .sender'),
    'say': ('持ち主に言葉を送る（ステーションにも出る）', 'Send a message to the owner (also shown on the station)'),
    'count': ('持ち物の数（物を省けば全部）', 'How many items it holds (all if no item given)'),
    'count_at': ('入れ物の中の数', 'How many items a container holds'),
    'free_slots': ('持ち物の空いているスロットの数', 'Number of empty inventory slots'),
    'block': ('その場所のブロックの ID（"minecraft:stone" など）', 'Block ID at a place ("minecraft:stone"...)'),
    'redstone': ('その場所が受けているレッドストーン信号（0〜15）', 'Redstone signal a place receives (0 to 15)'),
    'here': ('今いる場所', 'Where the android is now'),
    'station': ('ステーションの場所', 'Where the station is'),
    'point': ('名前か番号の場所（巡回路の欄の作業位置カード）', 'A place by name or number (route slot cards)'),
    'points': ('場所の名前の一覧', 'List of place names'),
    'is_day': ('昼なら True', 'True during the day'),
    'time': ('一日の時刻（0〜23999）', 'Time of day (0 to 23999)'),
    'charge': ('充電の残り（%）', 'Charge left (%)'),
    'health': ('体力の残り（%）', 'Health left (%)'),
    'pos': ('座標の場所を作る', 'Make a place from coordinates'),
    'range': ('数の並び（for で使う）', 'A run of numbers (for loops)'),
    'len': ('長さ', 'Length'),
    'str': ('文字列にする', 'Convert to a string'),
    'int': ('整数にする', 'Convert to an integer'),
    'float': ('小数にする', 'Convert to a float'),
    'bool': ('真偽にする', 'Convert to True or False'),
    'abs': ('絶対値', 'Absolute value'),
    'min': ('いちばん小さいもの', 'Smallest value'),
    'max': ('いちばん大きいもの', 'Largest value'),
    'round': ('四捨五入（偶数への丸め）', 'Round (to even)'),
    'randint': ('a 以上 b 以下の乱数', 'Random integer from a to b'),
    'list': ('リストにする', 'Make a list'),
    'sorted': ('並べ替えたリスト', 'Sorted copy of a list'),
    'sum': ('合計', 'Sum'),
}

# 誤りの説明（%s は Java の ProgramError の引数の順）
ERRORS = {
    'arg_count': ('%s() は引数を %s 個受け取る（%s 個渡した）', '%s() takes %s argument(s), got %s'),
    'bad_char': ('使えない文字: %s', 'Unexpected character: %s'),
    'bad_index': ('添え字は整数にする（%s を渡した）', 'Index must be an integer, not %s'),
    'bad_item': ('物の名前が分からない: %s', 'Unknown item: %s'),
    'bad_number': ('数の書き方が正しくない: %s', 'Malformed number: %s'),
    'bad_operand': ('%s は %s に使えない', 'Cannot use %s on %s'),
    'bad_operands': ('%s は %s と %s の間で使えない', 'Cannot use %s between %s and %s'),
    'bad_radius': ('範囲 %s は 0 から %s までにする', 'Range %s must be from 0 to %s'),
    'not_mob': ('モブ（mobs() の結果）が要る（%s を渡した）', 'Expected a mob (from mobs()), not %s'),
    'mob_gone': ('そのモブはもういない（.alive で確かめられる）', 'That mob is gone (check with .alive)'),
    'bad_filter': ('絞り込み %s が分からない（"hostile"・"animal"・"all"・モブの ID・"#タグ"、頭に ! で除く）',
                   'Unknown filter %s (use "hostile", "animal", "all", a mob ID or "#tag", with ! in front to exclude)'),
    'not_lever': ('%s はレバーではない', '%s is not a lever'),
    'bad_topic': ('知らせの名前 %s は1〜32文字にする', 'Message topic %s must be 1 to 32 characters'),
    'cannot_send': ('%s は知らせで送れない（数・文字・真偽・None・場所・モブ・そのリスト）', 'Cannot send %s (numbers, text, booleans, None, places, mobs and lists of them)'),
    'cannot_attack': ('%s とは戦えない（護衛のアンドロイド・飼われた動物・ボス）', 'Cannot fight %s (guard androids, tamed pets and bosses)'),
    'bad_target': ('ここには代入できない', 'Cannot assign to this'),
    'bad_value': ('%s() に渡した値が正しくない', 'Invalid value passed to %s()'),
    'builtin_name': ('%s は組み込みの関数の名前なので使えない', '%s is the name of a built-in function'),
    'convert': ('%s を %s にできない', 'Cannot convert %s to %s'),
    'dedent': ('字下げが外側のどの行ともそろっていない', 'Indentation does not match any outer level'),
    'div_zero': ('0 で割った', 'Division by zero'),
    'duplicate_function': ('関数 %s がもう定められている', 'Function %s is already defined'),
    'duplicate_param': ('引数の名前 %s が重なっている', 'Duplicate parameter %s'),
    'empty_list': ('空のリストから取り出した', 'Pop from an empty list'),
    'expected': ('%s があるはずの所に %s がある', 'Expected %s but found %s'),
    'expected_indent': ('ここに字下げしたブロックが要る', 'Expected an indented block'),
    'index_range': ('添え字 %s が範囲の外（長さ %s）', 'Index %s out of range (length %s)'),
    'internal': ('動かせない命令に出会った', 'Internal error'),
    'needs_mining_upgrade': ('掘るには採掘アップグレードが要る', 'Mining needs a Mining Upgrade'),
    'nested_def': ('def はいちばん外側にだけ書ける', 'def is only allowed at the top level'),
    'no_attribute': ('%s に .%s はない', '%s has no attribute .%s'),
    'no_length': ('%s には長さがない', '%s has no length'),
    'no_method': ('%s に .%s() はない', '%s has no method .%s()'),
    'no_point': ('場所 %s がない（巡回路の欄の作業位置カード）', 'No place called %s (Work Location Cards in the route slots)'),
    'no_station': ('ステーションと同じ次元にいない', 'Not in the same dimension as the station'),
    'not_assignable': ('%s の中身は変えられない', 'Cannot change the contents of %s'),
    'not_callable': ('これは関数として呼べない', 'This cannot be called'),
    'not_in_list': ('%s はリストにない', '%s is not in the list'),
    'not_integer': ('整数が要る（%s を渡した）', 'Expected an integer, not %s'),
    'not_iterable': ('%s は順にたどれない', '%s is not iterable'),
    'not_number': ('数が要る（%s を渡した）', 'Expected a number, not %s'),
    'not_position': ('場所（pos() か場所の名前）が要る（%s を渡した）', 'Expected a place (pos() or a place name), not %s'),
    'not_text': ('文字列が要る（%s を渡した）', 'Expected a string, not %s'),
    'outside_function': ('return は関数の中だけ', 'return outside a function'),
    'outside_loop': ('%s はループの中だけ', '%s outside a loop'),
    'overflow': ('数が大きすぎる', 'Number too large'),
    'stray': ('%s の前に対応する if がない', '%s without a matching if'),
    'too_big': ('リスト・文字列・変数が多すぎる', 'Too many items, characters or variables'),
    'too_deep': ('呼び出しが深すぎる（終わらない再帰？）', 'Calls nested too deeply (endless recursion?)'),
    'too_far': ('%s はステーションから %s ブロックより遠い', '%s is more than %s blocks from the station'),
    'too_long': ('プログラムが長すぎる（%s 文字まで）', 'Program too long (up to %s characters)'),
    'unclosed': ('かっこが閉じていない', 'Unclosed bracket'),
    'undefined': ('%s はまだ決めていない名前', 'Name %s is not defined'),
    'unexpected_indent': ('ここで字下げしてはいけない', 'Unexpected indent'),
    'unknown_function': ('関数 %s はない', 'Unknown function %s'),
    'unsupported': ('%s は使えない', '%s is not supported'),
    'unterminated_string': ('文字列が閉じていない', 'Unterminated string'),
}

UI = {
    'program.singulo.error_at': ('%s行目: %s', 'Line %s: %s'),
    'program.singulo.stopped': ('%s のプログラムが止まった: %s', 'The program of %s stopped: %s'),
    'program.singulo.say': ('<%s> %s', '<%s> %s'),
    'tooltip.singulo.program_card.empty': ('白紙: 手に持って使うと、プログラムを書ける', 'Blank: use it in hand to write a program'),
    'tooltip.singulo.program_card.lines': ('%s 行', '%s lines'),
    'gui.singulo.program.title': ('プログラムカード', 'Program Card'),
    'gui.singulo.program.functions': ('関数', 'Functions'),
    'gui.singulo.program.ok': ('誤りなし', 'No errors'),
    'gui.singulo.program.tab.functions': ('関数', 'Functions'),
    'gui.singulo.program.tab.places': ('場所', 'Places'),
    'gui.singulo.program.tab.examples': ('例', 'Examples'),
    'gui.singulo.program.tab.output': ('出力', 'Output'),
    'gui.singulo.program.search': ('名前か説明で探す', 'Search names or descriptions'),
    'gui.singulo.program.category.action': ('動き（時間がかかる）', 'Actions (take time)'),
    'gui.singulo.program.category.query': ('調べる', 'Queries'),
    'gui.singulo.program.category.value': ('値の計算', 'Values'),
    'gui.singulo.program.completion': ('候補', 'Suggest'),
    'gui.singulo.program.completion.hint': ('打っている語の候補（関数・変数・場所の名前・物の ID）を出す。↑↓で選び、Tab か Enter で入れる。Ctrl+Space でいつでも出せる',
                                           'Suggests the word being typed (functions, variables, place names, item IDs). Pick with the arrow keys and insert with Tab or Enter. Ctrl+Space shows it any time'),
    'gui.singulo.program.hints': ('ヒント', 'Hints'),
    'gui.singulo.program.hints.hint': ('呼んでいる関数の引数（今の引数に下線）と説明を出す', 'Shows the arguments of the function being called (the current one underlined) and what it does'),
    'gui.singulo.program.done': ('保存して閉じる（Esc でも。Ctrl+S は保存だけ）', 'Save and close (Esc does too; Ctrl+S only saves)'),
    'gui.singulo.program.discard': ('保存せずに閉じる', 'Close without saving'),
    'gui.singulo.program.discard.confirm': ('もう一度押すと、変更を捨てて閉じる', 'Press again to throw away the changes and close'),
    'gui.singulo.program.restart.hint': ('受け持つアンドロイドのプログラムを最初から動かす（変えていれば、保存してから）',
                                         'Runs the program of the androids from the start (saving first if changed)'),
    'gui.singulo.program.places.hand': ('組立ステーションの画面の「編集」から開くと、ステーションの場所（巡回路の欄の作業位置カード）が並ぶ。ここでは p1〜p8 の名前を使える',
                                        'Open the editor from Edit on the Android Assembly Station screen to list its places (Work Location Cards in the route slots). Here you can use the names p1 to p8'),
    'gui.singulo.program.places.empty': ('p%s（空き）', 'p%s (empty)'),
    'gui.singulo.program.places.click': ('クリックすると、名前を入れる', 'Click to insert the name'),
    'gui.singulo.program.output.hand': ('組立ステーションの画面の「編集」から開くと、動いている様子と say の言葉が出る',
                                        'Open the editor from Edit on the Android Assembly Station screen to see the program running and what say prints'),
    'gui.singulo.program.output.empty': ('まだ言葉はない（say で出せる）', 'Nothing printed yet (use say)'),
    'gui.singulo.program.none': ('見つからない', 'Nothing found'),
    'gui.singulo.program.warn.no_point': ('%s行目: 場所 %s がステーションにない', 'Line %s: the station has no place called %s'),
    'gui.singulo.program.snippet.carry.title': ('入れ物から入れ物へ運ぶ', 'Carry between containers'),
    'gui.singulo.program.snippet.carry.desc': ('p1 に物があれば、取って p2 へ入れる。5秒ごとに見る', 'Takes whatever is in p1 and puts it in p2, checking every 5 seconds'),
    'gui.singulo.program.snippet.furnace.title': ('かまどで焼く', 'Smelt in a furnace'),
    'gui.singulo.program.snippet.furnace.desc': ('p1 の原石を p2 のかまどに入れ、焼けた物を p3 へ運ぶ', 'Feeds raw iron from p1 into the furnace at p2 and carries the ingots to p3'),
    'gui.singulo.program.snippet.dig.title': ('下へ掘る', 'Dig down'),
    'gui.singulo.program.snippet.dig.desc': ('p1 から下へ5つ掘り、戻って p2 へ入れる（採掘アップグレードが要る）', 'Mines five blocks down from p1, comes back and puts them in p2 (needs a Mining Upgrade)'),
    'gui.singulo.program.snippet.day.title': ('昼だけ見張る', 'Watch during the day'),
    'gui.singulo.program.snippet.day.desc': ('昼は p1 に立ち、夜はステーションへ戻る', 'Stands at p1 during the day and goes back to the station at night'),
    'gui.singulo.program.snippet.guard.title': ('持ち場で見張る', 'Guard a post'),
    'gui.singulo.program.snippet.guard.desc': ('p1 のブロックの上で待ち、5ブロック以内に入ったモブと、倒れるか範囲を出るまで戦う。自分からはほかで戦わない（充電はいつもどおり）',
                                               'Waits on top of the block at p1 and fights any mob within 5 blocks until it dies or leaves. It fights nowhere else on its own (recharging works as usual)'),
    'gui.singulo.program.snippet.team.title': ('仲間と守る', 'Guard as a team'),
    'gui.singulo.program.snippet.team.desc': ('敵を見つけたら近くの仲間に知らせて戦う。知らせを受けたら、知らせたアンドロイドの所の敵を倒しに行く',
                                              'Tells nearby teammates when it spots an enemy and fights it; on a message it goes to help defeat the enemy near the sender'),
    'gui.singulo.program.snippet.alarm.title': ('敵が来たらレバー', 'Lever when enemies come'),
    'gui.singulo.program.snippet.alarm.desc': ('p1 から10ブロックに敵対モブがいる間は p2 のレバーを入れ、いなくなったら切る',
                                               'Turns the lever at p2 on while hostile mobs are within 10 blocks of p1, and off when they are gone'),
    'gui.singulo.program.snippet.signal.title': ('信号で動かす', 'React to a signal'),
    'gui.singulo.program.snippet.signal.desc': ('p1 にレッドストーン信号が来たら、p2 のブロック（ボタン・レバーなど）を使う', 'Uses the block at p2 (button, lever...) when p1 receives a redstone signal'),
    'gui.singulo.program.snippet.function.title': ('関数を作る', 'Write a function'),
    'gui.singulo.program.snippet.function.desc': ('def で手順をまとめ、return で結果を返す', 'Groups steps with def and returns a result with return'),
    'gui.singulo.android_station.program.edit': ('編集', 'Edit'),
    'gui.singulo.android_station.program.edit.hint': ('カードのプログラムを書く画面を開く（ステーションの場所の名前の候補と、動いている行・say の言葉が出る）',
                                                      'Opens the editor for the card (with the place names of the station, the running line and what say prints)'),
    'gui.singulo.program.cursor': ('%s行 %s列', 'Ln %s, Col %s'),
    'gui.singulo.android_station.mode.4': ('コード', 'Code'),
    'gui.singulo.android_station.mode.4.hint': (
        'プログラムカード（下の段の左の欄）の通りに動く。場所は上の段の作業位置カード（金床で付けた名前か p1〜p8）。戦い・充電のあいだは止まり、終わると続きから動く。',
        'Follows the Program Card (left slot of the lower row). Places are the Work Location Cards in the upper row (anvil names, or p1 to p8). It pauses to fight or recharge and then carries on.'),
    'gui.singulo.android_station.program.0': ('カードが白紙', 'The card is blank'),
    'gui.singulo.android_station.program.1': ('%s行目を実行中', 'Running line %s'),
    'gui.singulo.android_station.program.2': ('待っている（%s行目）', 'Waiting (line %s)'),
    'gui.singulo.android_station.program.3': ('終わった', 'Finished'),
    'gui.singulo.android_station.program.4': ('%s行目で誤り', 'Error on line %s'),
    'gui.singulo.android_station.program.5': ('中断中（%s行目）', 'Paused (line %s)'),
    'gui.singulo.android_station.program.no_card': ('プログラムカードを入れる', 'Insert a Program Card'),
    'gui.singulo.android_station.program.restart': ('もう一度', 'Restart'),
    'gui.singulo.android_station.program.restart.hint': ('受け持つアンドロイドのプログラムを、最初から動かし直す', 'Runs the program of this station’s androids again from the start'),
    'gui.singulo.android_station.program.points.hint': ('場所 p%s: 作業位置カードを入れる。金床で名前を付けると、その名前でも呼べる',
                                                        'Place p%s: insert a Work Location Card. Name it with an anvil to use that name too'),
}
UI.update({f'program.singulo.fn.{k}': v for k, v in FUNCTIONS.items()})
UI.update({f'program.singulo.error.{k}': v for k, v in ERRORS.items()})

# ハンドブックの「アンドロイド」の章の最後に足すページ（tools/guide.py が読む）
GUIDE_PAGE = (
    'program', 'プログラムで動かす',
    'プログラムカードを手に持って使うと、Python に似た書き方でアンドロイドの動きを書けます。組立ステーションの動き方を「コード」にし、下の段の左の欄にカードを、上の段に作業位置カードを入れると、受け持つアンドロイドがその通りに動きます。場所は作業位置カードの金床で付けた名前か p1〜p8 で呼べます（pos(x, y, z) でも可。ステーションから64ブロック以内）。\n'
    '書けるもの: 変数・if / elif / else・while・for i in range(n)・def と return・リスト・文字列。字下げはスペース4つ（Tab キーで入る）。# から行末はコメント。\n'
    '動き: go(p) 行く・take(p, 物, 数) 取る・put(p, 物, 数) 入れる・use(p) 使う・mine(p) 掘る（採掘アップグレード）・wait(秒) 待つ・home() 戻る。物は "coal" や "minecraft:iron_ingot"、タグは "#c:ingots"。\n'
    '調べる: count(物)・count_at(p, 物)・free_slots()・block(p)・redstone(p)・is_day()・time()・charge()・health()。say(…) で持ち主に言葉を送れます。\n'
    '戦う: mobs(p, r) で p から r ブロックのモブの一覧（近い順）が取れ、モブの .name・.type・.health・.alive・.baby・.named・.pos を読めます。mobs(p, r, ["hostile", "!creeper"]) のように3番目で絞れます（"hostile"・"animal"・"all"・"zombie" などの ID・"#minecraft:undead" などのタグ。頭に ! で除く）。matches(m, 絞り込み) で1体を確かめられます。attack(m, p, r) は、相手が倒れるか p から r ブロックの外へ出るまで戦います。auto_fight(False) にすると、自分で敵を探したり打たれて仕返ししたりせず、attack の相手とだけ戦います（充電・修理はいつもどおり）。\n'
    '仲間と動く: send("enemy", m, 32) で32ブロック以内の仲間のアンドロイド（同じ持ち主か登録者の、コードで動くもの）へ知らせを送り、受ける側は msg = receive("enemy") で1つ取り出して msg.data（送った値）・msg.pos（送ったアンドロイドの場所）・msg.sender を読めます（なければ None）。lever(p, True) でレバーを入れ、lever(p, False) で切ります。mobs を調べて use(p) でボタンを押せば、敵が来たときの合図にもなります。\n'
    '例: while True: のブロックで、count_at("かまど", "iron_ingot") が 0 より多ければ take("かまど", "iron_ingot") と put("チェスト") をして、wait(5)。\n'
    '誤りがあると、書く画面の下とステーションに行と理由が出ます（動かしている途中の誤りは持ち主にも知らされる）。1 tick に動かす手数には上限があり、終わらないループでもサーバーは止まりません。戦い・充電のあいだは止まり、終わると続きから動きます。ワールドを開き直したときや、カード・場所を変えたときは最初から動きます（ステーションの「もう一度」でも）。',
    'Programming androids',
    'Use a Program Card in hand to write what an android does in a Python-like language. Set the Android Assembly Station mode to Code, put the card in the left slot of the lower row and Work Location Cards in the upper row, and the androids of the station follow the program. Places are called by the anvil name of each Work Location Card or p1 to p8 (pos(x, y, z) also works, within 64 blocks of the station).\n'
    'You can use variables, if / elif / else, while, for i in range(n), def and return, lists and strings. Indent with four spaces (the Tab key adds them). A # starts a comment.\n'
    'Actions: go(p), take(p, item, n), put(p, item, n), use(p), mine(p) (needs a Mining Upgrade), wait(seconds) and home(). Items are written as "coal" or "minecraft:iron_ingot", tags as "#c:ingots".\n'
    'Queries: count(item), count_at(p, item), free_slots(), block(p), redstone(p), is_day(), time(), charge() and health(). say(...) sends a message to the owner.\n'
    'Fighting: mobs(p, r) lists the mobs within r blocks of p (nearest first), and you can read .name, .type, .health, .alive, .baby, .named and .pos of a mob. Narrow the list with a third argument such as ["hostile", "!creeper"] ("hostile", "animal", "all", IDs like "zombie", tags like "#minecraft:undead", and ! in front to exclude). matches(m, filter) checks one mob. attack(m, p, r) fights until the mob dies or leaves r blocks around p. With auto_fight(False) it no longer seeks enemies or fights back on its own and only fights attack targets (recharging and repairs work as usual).\n'
    'Teamwork: send("enemy", m, 32) sends a message to teammate androids running code within 32 blocks (same owner or members). The receiver takes one with msg = receive("enemy") (None if there is none) and reads msg.data (the value sent), msg.pos (where the sender was) and msg.sender. lever(p, True) turns a lever on and lever(p, False) turns it off; checking mobs and pressing a button with use(p) works as an alarm.\n'
    'Example: inside while True:, if count_at("furnace", "iron_ingot") is more than 0, take("furnace", "iron_ingot") and put("chest"), then wait(5).\n'
    'Errors show the line and reason at the bottom of the editor and on the station (errors while running are also sent to the owner). Each tick runs a limited number of steps, so an endless loop never stops the server. The program pauses to fight or recharge and then carries on. It starts over when the world is reopened, when the card or places change, or with Restart on the station.',
)
