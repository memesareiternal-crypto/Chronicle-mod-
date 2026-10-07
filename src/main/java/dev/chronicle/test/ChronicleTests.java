package dev.chronicle.test;

import dev.chronicle.Chronicle;
import dev.chronicle.entity.MatterBody;
import dev.chronicle.power.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.List;

@GameTestHolder(Chronicle.ID)
@PrefixGameTestTemplate(false)
public final class ChronicleTests {
    private static BlockPos point(GameTestHelper h) { return h.absolutePos(new BlockPos(1, 3, 1)); }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h) {
        var p = new net.minecraftforge.common.util.FakePlayer(h.getLevel(), new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "chronicle-test"));
        p.setPos(Vec3.atCenterOf(point(h))); return p;
    }
    private static MatterBody stone(GameTestHelper h) { BlockPos pos=point(h); h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3); return MatterBody.capture(h.getLevel(),List.of(pos),null); }
    @GameTest(template="empty") public static void chest_transfer_is_exact(GameTestHelper h) {
        BlockPos from=point(h); h.getLevel().setBlock(from,Blocks.CHEST.defaultBlockState(),3);
        ((ChestBlockEntity)h.getLevel().getBlockEntity(from)).setItem(0,new ItemStack(Items.DIAMOND,7));
        MatterBody body=MatterBody.capture(h.getLevel(),List.of(from),null);
        h.assertTrue(body!=null,"Capture must succeed"); h.assertTrue(h.getLevel().getBlockState(from).isAir(),"Source must be empty");
        h.assertItemEntityNotPresent(Items.DIAMOND,new BlockPos(1,3,1),3);
        h.assertTrue(body.place(from.above(2),null),"Placement must succeed");
        var chest=(ChestBlockEntity)h.getLevel().getBlockEntity(from.above(2));
        h.assertTrue(chest.getItem(0).is(Items.DIAMOND)&&chest.getItem(0).getCount()==7,"All seven diamonds must survive exactly once");h.succeed();
    }
    @GameTest(template="empty") public static void obstruction_is_atomic(GameTestHelper h) {
        BlockPos p=point(h);h.getLevel().setBlock(p,Blocks.STONE.defaultBlockState(),3);h.getLevel().setBlock(p.east(),Blocks.GOLD_BLOCK.defaultBlockState(),3);
        var body=MatterBody.capture(h.getLevel(),List.of(p,p.east()),null);BlockPos to=p.above(2);h.getLevel().setBlock(to.east(),Blocks.BEDROCK.defaultBlockState(),3);
        h.assertFalse(body.place(to,null),"Blocked placement must fail");h.assertTrue(body.isAlive()&&body.mass()==2,"Both stored blocks must survive");h.assertTrue(h.getLevel().getBlockState(to).isAir(),"No partial writes");
        h.getLevel().setBlock(to.east(),Blocks.AIR.defaultBlockState(),3);h.assertTrue(body.place(to,null),"Retry must work");h.assertTrue(h.getLevel().getBlockState(to.east()).is(Blocks.GOLD_BLOCK),"Gold must survive retry");h.succeed();
    }
    @GameTest(template="empty") public static void held_bodies_integrate_velocity(GameTestHelper h) {
        var body=stone(h);double before=body.getX();body.held();body.setDeltaMovement(new Vec3(.4,0,0));body.tick();h.assertTrue(body.getX()>before+.3,"Held body must move");body.discard();h.succeed();
    }
    @GameTest(template="empty") public static void rotation_transforms_block_states(GameTestHelper h) {
        BlockPos p=point(h);h.getLevel().setBlock(p,Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING,Direction.NORTH),3);h.getLevel().setBlock(p.east(),Blocks.GOLD_BLOCK.defaultBlockState(),3);
        var body=MatterBody.capture(h.getLevel(),List.of(p,p.east()),null);body.rotate(1);h.assertTrue(body.width()==1&&body.depth()==2,"Footprint must rotate");
        h.assertTrue(body.place(p.above(2),null),"Rotated placement must work");h.assertTrue(h.getLevel().getBlockState(p.above(2)).getValue(ChestBlock.FACING)==Direction.EAST,"Facing must rotate clockwise");h.assertTrue(h.getLevel().getBlockState(p.above(2).south()).is(Blocks.GOLD_BLOCK),"Offsets must rotate");h.succeed();
    }
    @GameTest(template="empty") public static void saved_matter_survives_restart(GameTestHelper h) {
        var original=stone(h);original.rotate(3);var nbt=new CompoundTag();original.saveWithoutId(nbt);original.discard();
        var copy=Chronicle.MATTER.get().create(h.getLevel());copy.load(nbt);
        h.assertTrue(copy.mass()==1&&copy.turn()==3,"Stored contents and rotation must persist");h.assertFalse(copy.isHeld(),"Saved concentration must not strand objects after restart");
        h.assertTrue(copy.place(point(h).above(2),null),"Reloaded object must place");h.succeed();
    }
    @GameTest(template="empty") public static void bedrock_capture_leaves_world_unchanged(GameTestHelper h) {
        BlockPos p=point(h);h.getLevel().setBlock(p,Blocks.BEDROCK.defaultBlockState(),3);h.getLevel().setBlock(p.east(),Blocks.GOLD_BLOCK.defaultBlockState(),3);
        h.assertTrue(MatterBody.capture(h.getLevel(),List.of(p,p.east()),null)==null,"Unbreakable regions must be rejected");h.assertTrue(h.getLevel().getBlockState(p.east()).is(Blocks.GOLD_BLOCK),"Rejected capture must not remove other blocks");h.succeed();
    }
    @GameTest(template="empty") public static void repeated_use_never_weakens_power(GameTestHelper h) {
        var p=player(h);Potential.maximize(p);double force=Potential.force(p);float health=p.getHealth();
        p.getPersistentData().getCompound("chronicle_potential").putDouble("strain",1000000);
        for(int i=0;i<1000;i++)Potential.practice(p,100);
        h.assertTrue(Potential.force(p)==force&&p.getHealth()==health,"Unlimited use must preserve force and health");
        h.assertFalse(Potential.data(p).contains("strain"),"Legacy exertion data must be removed");
        Potential.active(p,false);h.assertTrue(Potential.acquired(p)&&!Potential.active(p),"Toggle preserves ownership");
        Potential.remove(p);h.assertFalse(Potential.acquired(p),"Remove revokes power");h.succeed();
    }
    @GameTest(template="empty") public static void hold_restores_mob_state(GameTestHelper h) {
        var p=player(h);Potential.grant(p);var mob=h.spawn(EntityType.ZOMBIE,new BlockPos(2,3,2));mob.setNoAi(false);mob.setNoGravity(false);
        var hold=Physics.take(p,mob);h.assertTrue(hold!=null,"Hold must be acquired");h.assertTrue(hold.steer(p,mob.position().add(0,1,0),false),"Nearby hold must be maintained");h.assertTrue(mob.isNoAi()&&mob.isNoGravity(),"Hold must actually suspend AI and gravity");hold.close();
        h.assertFalse(mob.isNoAi()||mob.isNoGravity(),"Release must restore original AI and gravity");h.succeed();
    }
    @GameTest(template="empty") public static void input_is_bounded(GameTestHelper h) {
        Intent intent=new Intent(-1,Integer.MAX_VALUE);h.assertTrue(intent.buttons()==4095&&intent.wheel()==1,"Untrusted input must be bounded");h.succeed();
    }
}
