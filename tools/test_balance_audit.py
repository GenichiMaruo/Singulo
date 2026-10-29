"""Exact-arithmetic checks for the audit, without starting Minecraft or loading its ZIP."""
import unittest
from balance_audit import Model, Bill


class BalanceArithmeticTests(unittest.TestCase):
    def model(self):
        m=Model.__new__(Model);m.all=[];m.routes={}
        return m

    def test_crafting_batches_reuse_leftovers(self):
        m=self.model();m.routes['wire']=m.synthetic('wire',{'minecraft:raw_copper':3},8)
        b=Bill(m,True);b.need('wire',1);b.need('wire',1)
        self.assertEqual(b.raw['minecraft:raw_copper'],3)
        self.assertEqual(b.stock['wire'],6)

    def test_amortized_bill_is_fractional_without_rounding(self):
        m=self.model();m.routes['wire']=m.synthetic('wire',{'minecraft:raw_copper':3},8)
        b=Bill(m);b.need('wire',1)
        self.assertEqual(float(b.raw['minecraft:raw_copper']),3/8)

    def test_metal_mass_is_storage_block_mass_not_ingot_count(self):
        m=self.model();m.routes['minecraft:iron_block']=m.synthetic('iron block',{'minecraft:raw_iron':9})
        m.routes['metal1']={**m.synthetic('metal1',{}),'mass':{'amount':9,'metal':True}}
        m.routes['metal2']=m.synthetic('metal2',{'metal1':9})
        b=Bill(m,True);b.need('metal2',1)
        self.assertEqual(b.raw['minecraft:raw_iron'],243)

    def test_catalyst_life_is_shared_across_operations(self):
        m=self.model();m.routes['singulo:time_crystal_catalyst']=m.synthetic('crystal',{'raw_seed':1})
        m.routes['module']=m.synthetic('module',{})
        m.routes['module']['inputs']=[{'options':['singulo:time_crystal_catalyst'],'count':1,'uses':36000,'tier':4}]
        b=Bill(m,True);b.need('module',1);b.need('module',1)
        self.assertEqual(b.raw['raw_seed'],2)
        self.assertEqual(b.use_stock['singulo:time_crystal_catalyst'],36000)
        self.assertEqual(b.life['singulo:time_crystal_catalyst'],72000)

    def test_free_liquid_nitrogen_keeps_energy_when_tower_speeds_up(self):
        m=self.model()
        m.routes['nitrogen']={**m.synthetic('nitrogen',{},100,20,1250),'station':'cryogenic_cooling_tower'}
        for stage,energy in [(2,25004),(3,25000)]:
            b=Bill(m,True,stage);b.need('nitrogen',100)
            self.assertEqual(b.energy,energy)

    def test_large_accelerator_halves_recipe_work_and_energy(self):
        m=self.model()
        m.routes['metal']={**m.synthetic('metal',{},1,200,1000),'station':'particle_accelerator','min_size':16}
        b=Bill(m,True,4);b.need('metal',1)
        self.assertEqual(b.energy,100000)
        self.assertEqual(b.work['particle_accelerator'],100)

    def test_cycles_fail_instead_of_becoming_free_materials(self):
        m=self.model();m.routes['a']=m.synthetic('a',{'b':1});m.routes['b']=m.synthetic('b',{'a':1})
        with self.assertRaisesRegex(RuntimeError,'cycle'):Bill(m).need('a',1)


if __name__=='__main__':unittest.main()
