import io.github.genichimaruo.singulo.compat.PocketAttachmentMigration;
import net.minecraft.nbt.*;
public final class PocketAttachmentMigrationTest {
 private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
 public static void main(String[] args){
  var old=new CompoundTag();var entries=new ListTag();var item=new CompoundTag();
  item.putString("id","unavailable_mod:preserved_item");item.putInt("count",37);
  var components=new CompoundTag();components.putString("unknown:component","opaque-value");item.put("components",components);
  entries.add(item);entries.add(new CompoundTag());old.put(PocketAttachmentMigration.KEY,entries);
  old.putString("other:attachment","untouched");var migrated=PocketAttachmentMigration.migrate(old);
  check(old.get(PocketAttachmentMigration.KEY) instanceof ListTag,"source mutated");
  var body=(CompoundTag)migrated.get(PocketAttachmentMigration.KEY);
  check(entries.equals(body.get("items")),"raw data lost");
  check(old.get("other:attachment").equals(migrated.get("other:attachment")),"other attachment changed");
  check(PocketAttachmentMigration.migrate(migrated)==migrated,"not idempotent");
  var roundtrip=CompoundTag.CODEC.parse(NbtOps.INSTANCE, CompoundTag.CODEC.encodeStart(NbtOps.INSTANCE,migrated).getOrThrow()).getOrThrow();
  check(roundtrip.equals(migrated),"new format roundtrip");
  var invalid=new CompoundTag();invalid.putString(PocketAttachmentMigration.KEY,"invalid-opaque");
  check(PocketAttachmentMigration.migrate(invalid)==invalid,"malformed data silently replaced");
  var absent=new CompoundTag();check(PocketAttachmentMigration.migrate(absent)==absent,"absent changed");
  for(String key:new String[]{"singulo:records","singulo:prototype_memories","singulo:discovered_ruins"}) {
   var source=new CompoundTag();var list=new ListTag();
   if(key.endsWith("discovered_ruins")){var ruin=new CompoundTag();ruin.putString("id","future:unknown_ruin");ruin.putLong("pos",123456789L);list.add(ruin);}else list.add(StringTag.valueOf("unknown_future_record"));
   source.put(key,list);var result=PocketAttachmentMigration.migrate(source);
   check(source.get(key) instanceof ListTag,"legacy list mutated");
   check(((CompoundTag)result.get(key)).get("entries").equals(list),"legacy data lost");
   var saved=CompoundTag.CODEC.parse(NbtOps.INSTANCE,CompoundTag.CODEC.encodeStart(NbtOps.INSTANCE,result).getOrThrow()).getOrThrow();
   check(saved.equals(result) && PocketAttachmentMigration.migrate(saved)==saved,"roundtrip or repeated migration changed data");
  }
  System.out.println("Attachment migration: 16 checks PASS");
 }
}
