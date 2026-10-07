package dev.chronicle.test;

import com.mojang.authlib.GameProfile;
import dev.chronicle.Chronicle;
import dev.chronicle.Settings;
import dev.chronicle.ServerEvents;
import dev.chronicle.power.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.gametest.*;
import java.util.*;

@GameTestHolder(Chronicle.ID)
@PrefixGameTestTemplate(false)
public final class TatsumakiTests {
    private static ServerPlayer player(GameTestHelper h){
        var p=new FakePlayer(h.getLevel(),new GameProfile(UUID.randomUUID(),"tatsumaki-audit"));
        p.setPos(Vec3.atCenterOf(h.absolutePos(new BlockPos(1,3,1))));p.setYRot(0);p.setXRot(0);p.tickCount=1;
        Potential.maximize(p);return p;
    }
    private static void input(ServerPlayer p,int bits){Concentration.accept(p,new Intent(bits,0));Concentration.tick(p);p.tickCount++;}
    @GameTest(template="empty") public static void right_click_grabs_a_crowd_without_crouching(GameTestHelper h){
        var p=player(h);var crowd=new ArrayList<Zombie>();
        try{
            for(int n=0;n<120;n++){var mob=h.spawn(EntityType.ZOMBIE,new BlockPos(1,4,4));mob.setPos(p.getX()+(n%10-4.5)*.05,p.getY()+.3,p.getZ()+3+n/10*.05);mob.setNoAi(true);crowd.add(mob);}
            input(p,Intent.ACT);
            long controlled=crowd.stream().filter(Physics::controlled).count();
            h.assertTrue(controlled==120,"An ordinary right click must grip the entire aimed crowd, not just the ray target");
            p.setYRot(70);input(p,Intent.ACT);
            h.assertTrue(crowd.stream().allMatch(e->e.getDeltaMovement().length()>.1),"Every member must respond to aim movement");
            input(p,0);
            h.assertTrue(crowd.stream().noneMatch(Physics::controlled),"Right-click release must release the entire crowd");
            h.succeed();
        }finally{Concentration.release(p);for(var e:crowd)e.discard();}
    }
    @GameTest(template="empty") public static void b_protection_applies_immediately_to_mod_style_damage(GameTestHelper h){
        try(var config=new TestConfigScope()){
            Settings.PASSIVE_DEFENSE.set(false);
            var p=player(h);var attacker=h.spawn(EntityType.ZOMBIE,new BlockPos(1,3,4));
            try{
                float before=PersonalDefense.reduce(p,p.damageSources().mobAttack(attacker),20);
                input(p,Intent.BARRIER);
                h.assertTrue(Concentration.protectedPersonally(p),"B must reinforce immediately on key press");
                var hurt=new LivingHurtEvent(p,p.damageSources().indirectMagic(attacker,attacker),20);
                new ServerEvents().hurt(hurt);
                h.assertTrue(hurt.getAmount()<before&&hurt.getAmount()>0,"Direct damage-event resistance must also cover sources without a projectile entity");
                h.assertTrue(PersonalDefense.reduce(p,p.damageSources().fellOutOfWorld(),20)==20,"Protection must not defeat void/administrative death");
                input(p,0);input(p,Intent.BARRIER);
                h.assertFalse(Concentration.protectedPersonally(p),"Second B press must disable protection");
                h.succeed();
            }finally{Concentration.release(p);attacker.discard();}
        }
    }
    @GameTest(template="empty") public static void double_space_flight_accelerates_strafes_brakes_and_restores_gravity(GameTestHelper h){
        var p=player(h);boolean permission=p.getAbilities().mayfly;
        try{
            input(p,Intent.JUMP);h.assertFalse(p.isNoGravity(),"One jump must remain a normal jump");
            input(p,0);input(p,Intent.JUMP);
            h.assertTrue(p.isNoGravity(),"Double Space must activate self-flight without right click");
            Vec3 v=Vec3.ZERO,look=new Vec3(0,0,1);
            for(int i=0;i<15;i++)v=FlightControl.integrate(v,look,new Intent(Intent.RIGHT|Intent.JUMP,0),4,.7,.4);
            h.assertTrue(v.x< -1&&v.y>1,"Shared client prediction must accelerate sideways and vertically");
            for(int i=0;i<30;i++)v=FlightControl.integrate(v,look,Intent.IDLE,4,.7,.4);
            h.assertTrue(v.lengthSqr()==0,"No movement input must settle to exact effortless hovering");
            h.assertTrue(p.getAbilities().mayfly==permission,"Self-flight must not grant creative abilities");
            input(p,0);p.tickCount+=10;input(p,Intent.JUMP);input(p,0);input(p,Intent.JUMP);
            h.assertFalse(p.isNoGravity(),"A second double Space must restore gravity");
            h.succeed();
        }finally{Concentration.release(p);}
    }
    @GameTest(template="empty") public static void personal_protection_resists_another_psychics_enhanced_melee(GameTestHelper h){
        var defender=player(h);var attacker=player(h);
        try{
            input(defender,Intent.BARRIER);
            var source=defender.damageSources().playerAttack(attacker);
            float incoming=10+(float)Potential.force(attacker)*2;
            var hurt=new LivingHurtEvent(defender,source,10);
            new ServerEvents().hurt(hurt);
            h.assertTrue(Math.abs(hurt.getAmount()-PersonalDefense.reduce(defender,source,incoming))<.001,"Reinforcement must reduce the attacker's entire enhanced damage, not just its vanilla portion");
            h.succeed();
        }finally{Concentration.release(defender);Concentration.release(attacker);}
    }
    @GameTest(template="empty") public static void minimum_output_selects_a_single_block_at_maximum_level(GameTestHelper h){
        var p=player(h);Potential.data(p).putDouble("output",.05);
        h.assertTrue(Potential.area(p)==0,"Minimum output must be an exact one-block selection, even at level ten");
        h.assertTrue(Potential.targets(p)<=3,"Minimum output must preserve delicate entity control");
        Potential.data(p).putDouble("output",1);
        h.assertTrue(Potential.area(p)>=20&&Potential.blocks(p)>=30000&&Potential.reach(p)>128,"Maximum output must reach enormous terrain and range automatically");
        h.succeed();
    }
    @GameTest(template="empty") public static void g_gathers_and_retains_an_orbit_without_a_held_mouse_button(GameTestHelper h){
        try(var config=new TestConfigScope()){
        Settings.TERRAIN.set(false);
        var p=player(h);var first=h.spawn(EntityType.ZOMBIE,new BlockPos(1,3,4));var second=h.spawn(EntityType.ZOMBIE,new BlockPos(3,3,4));
        try{
            input(p,Intent.GRIP);input(p,0);
            h.assertTrue(Physics.controlled(first)&&Physics.controlled(second),"G tap must retain automatic group control");
            h.assertTrue(first.getDeltaMovement().length()>.1,"Orbit must physically steer targets");
            input(p,Intent.GRIP);
            h.assertFalse(Physics.controlled(first)||Physics.controlled(second),"Second G tap releases the orbit");
            h.succeed();
        }finally{Concentration.release(p);first.discard();second.discard();}
        }
    }
    @GameTest(template="empty") public static void personal_projectile_defense_is_configurable(GameTestHelper h){
        try(var config=new TestConfigScope()){
            var p=player(h);var arrow=new Arrow(h.getLevel(),p.getX(),p.getEyeY(),p.getZ()+4);arrow.setDeltaMovement(0,0,-1);
            h.getLevel().addFreshEntity(arrow);
            try{
                p.tickCount=3;Settings.PASSIVE_DEFENSE.set(false);PersonalDefense.intercept(p);
                h.assertTrue(arrow.getOwner()!=p,"Disabled passive defense must preserve ordinary projectile flight");
                Settings.PASSIVE_DEFENSE.set(true);PersonalDefense.intercept(p);
                h.assertTrue(arrow.getOwner()==p&&arrow.getDeltaMovement().z>0,"Enabled defense must physically reverse incoming arrows");
                h.succeed();
            }finally{Concentration.release(p);arrow.discard();}
        }
    }
    @GameTest(template="empty") public static void projected_field_suppresses_explosion_paths(GameTestHelper h){
        var p=player(h);var ally=h.spawn(EntityType.ZOMBIE,new BlockPos(1,4,4));
        try{
            for(int i=0;i<12;i++)input(p,Intent.BARRIER);
            input(p,0);
            var blast=ally.position().add(0,1,30);
            var blocks=new ArrayList<BlockPos>();blocks.add(ally.blockPosition());
            var explosion=new net.minecraft.world.level.Explosion(h.getLevel(),null,blast.x,blast.y,blast.z,4,blocks);
            var victims=new ArrayList<net.minecraft.world.entity.Entity>();victims.add(ally);
            var event=new net.minecraftforge.event.level.ExplosionEvent.Detonate(h.getLevel(),explosion,victims);
            Concentration.suppress(event);
            h.assertTrue(event.getAffectedBlocks().isEmpty()&&event.getAffectedEntities().isEmpty(),"A projected field must suppress terrain and entity blast paths crossing its surface");
            h.succeed();
        }finally{Concentration.release(p);ally.discard();}
    }
    @GameTest(template="empty") public static void an_empty_volley_does_not_turn_into_an_unrequested_force_burst(GameTestHelper h){
        var p=player(h);Potential.data(p).putDouble("output",.05);
        var first=h.spawn(EntityType.ZOMBIE,new BlockPos(1,4,4));var second=h.spawn(EntityType.ZOMBIE,new BlockPos(1,4,4));var observer=h.spawn(EntityType.ZOMBIE,new BlockPos(1,4,9));
        try{
            first.setNoAi(true);second.setNoAi(true);observer.setNoAi(true);
            input(p,Intent.ACT);
            h.assertTrue(Physics.controlled(first)&&Physics.controlled(second)&&!Physics.controlled(observer),"Delicate grip fixture must acquire exactly the nearby pair");
            input(p,Intent.FORCE|Intent.SNEAK);
            for(int i=0;i<12;i++)input(p,Intent.FORCE);
            input(p,0);
            h.assertTrue(observer.getDeltaMovement().lengthSqr()==0,"Finishing a debris volley must not start a separate pressure attack");
            h.succeed();
        }finally{Concentration.release(p);first.discard();second.discard();observer.discard();}
    }
    @GameTest(template="empty") public static void orbiting_real_blocks_intercept_an_incoming_arrow(GameTestHelper h){
        try(var config=new TestConfigScope()){
            Settings.TERRAIN.set(false);var p=player(h);
            // Capture fixture without player editing; then gather it using the real G path.
            BlockPos source=h.absolutePos(new BlockPos(3,4,4));h.getLevel().setBlock(source,Blocks.STONE.defaultBlockState(),18);
            var body=dev.chronicle.entity.MatterBody.capture(h.getLevel(),List.of(source),null);
            Arrow arrow=null;
            try{
                input(p,Intent.GRIP);input(p,0);
                h.assertTrue(Physics.controlled(body),"G must gather an existing real block carrier");
                Vec3 center=body.getBoundingBox().getCenter(),direction=center.subtract(p.getBoundingBox().getCenter()).normalize(),from=center.add(direction.scale(3));
                arrow=new Arrow(h.getLevel(),from.x,from.y,from.z);arrow.setDeltaMovement(direction.scale(-2));h.getLevel().addFreshEntity(arrow);
                input(p,0);input(p,0);
                h.assertTrue(arrow.getOwner()==p&&arrow.getDeltaMovement().dot(direction)>0,"Orbiting debris must physically deflect a crossing arrow");
                h.assertTrue(body.mass()==1,"Interception must preserve the actual shield block");
                h.succeed();
            }finally{Concentration.release(p);body.discard();if(arrow!=null)arrow.discard();}
        }
    }
    @GameTest(template="empty",timeoutTicks=200) public static void charged_downward_force_flattens_without_losing_matter(GameTestHelper h){
        var p=player(h);BlockPos seed=new BlockPos(10256,288,10000);
        var chunks=new HashSet<net.minecraft.world.level.ChunkPos>();
        for(BlockPos pos:BlockPos.betweenClosed(seed.offset(-2,-2,-2),seed.offset(10,16,10)))chunks.add(new net.minecraft.world.level.ChunkPos(pos));
        for(var c:chunks){h.getLevel().getChunk(c.x,c.z);h.getLevel().setChunkForced(c.x,c.z,true);}
        for(BlockPos pos:BlockPos.betweenClosed(seed.offset(-2,-2,-2),seed.offset(10,16,10)))h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),18);
        for(BlockPos pos:BlockPos.betweenClosed(seed,seed.offset(7,3,7)))h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),18);
        p.setPos(seed.getX()+3.5,seed.getY()+10,seed.getZ()+3.5);p.setXRot(90);
        Applications.terrainForce(p,false,35,body->{});
        h.succeedWhen(()->{
            for(BlockPos pos:BlockPos.betweenClosed(seed.above(),seed.offset(7,3,7)))h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Every excess cell above the plane must move");
            for(BlockPos pos:BlockPos.betweenClosed(seed,seed.offset(7,0,7)))h.assertTrue(h.getLevel().getBlockState(pos).is(Blocks.STONE),"The lowered plane must survive");
            var bodies=h.getLevel().getEntitiesOfClass(dev.chronicle.entity.MatterBody.class,new net.minecraft.world.phys.AABB(seed,seed.offset(10,16,10)).inflate(24));
            var excess=bodies.stream().filter(b->b.mass()==192).findFirst().orElse(null);
            h.assertTrue(excess!=null,"Flattening must preserve all 192 removed cells in one coherent mass");
            excess.discard();Concentration.release(p);for(var c:chunks)h.getLevel().setChunkForced(c.x,c.z,false);
        });
    }
    @GameTest(template="empty",timeoutTicks=200) public static void force_impact_displaces_terrain_and_preserves_container_contents(GameTestHelper h){
        var p=player(h);BlockPos seed=new BlockPos(10384,288,10000);
        var chunk=new net.minecraft.world.level.ChunkPos(seed);h.getLevel().getChunk(chunk.x,chunk.z);h.getLevel().setChunkForced(chunk.x,chunk.z,true);
        for(BlockPos pos:BlockPos.betweenClosed(seed.offset(-2,-2,-4),seed.offset(5,6,10)))h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),18);
        var wall=new ArrayList<BlockPos>();
        for(BlockPos pos:BlockPos.betweenClosed(seed.offset(0,0,6),seed.offset(3,3,7))){wall.add(pos.immutable());h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),18);}
        BlockPos chest=seed.offset(1,1,6);h.getLevel().setBlock(chest,Blocks.CHEST.defaultBlockState(),18);
        ((net.minecraft.world.level.block.entity.ChestBlockEntity)h.getLevel().getBlockEntity(chest)).setItem(0,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND,7));
        h.getLevel().setBlock(seed,Blocks.GOLD_BLOCK.defaultBlockState(),18);
        var missile=dev.chronicle.entity.MatterBody.capture(h.getLevel(),List.of(seed),null);
        missile.setPos(seed.getX()+1.5,seed.getY()+1,seed.getZ()+4);p.setPos(seed.getX()+1.5,seed.getY()+1,seed.getZ()-4);
        Impacts.resolve(p,missile,missile.position(),new Vec3(0,0,.1));
        h.assertTrue(h.getLevel().getBlockState(chest).is(Blocks.CHEST),"Gentle contact must not cause catastrophic edits");
        Impacts.resolve(p,missile,missile.position(),new Vec3(0,0,6));
        h.succeedWhen(()->{
            // Secondary terrain is launched immediately. Query its travel envelope instead
            // of a source-only box that it can leave before the next test assertion tick.
            var debris=h.getLevel().getEntitiesOfClass(dev.chronicle.entity.MatterBody.class,new net.minecraft.world.phys.AABB(seed,seed.offset(5,8,12)).inflate(64)).stream().filter(b->b!=missile&&!b.transferring()&&b.mass()==32&&b.cells().stream().anyMatch(c->c.data()!=null)).findFirst().orElse(null);
            h.assertTrue(debris!=null,"A strong impact must eject real secondary terrain with its original container");
            for(BlockPos pos:wall)h.assertTrue(h.getLevel().getBlockState(pos).isAir(),"Every impact cell must transfer into the completed debris");
            var stored=debris.cells().stream().filter(c->c.data()!=null).findFirst().orElseThrow().data();
            h.assertTrue(stored.getList("Items",10).getCompound(0).getByte("Count")==7,"Impact must keep all seven diamonds exactly once");
            debris.discard();missile.discard();h.getLevel().setChunkForced(chunk.x,chunk.z,false);Impacts.clear();
        });
    }
    @GameTest(template="empty",timeoutTicks=300) public static void ordinary_right_click_lifts_a_large_terrain_mass(GameTestHelper h){
        var p=player(h);BlockPos seed=new BlockPos(10128,288,10000);
        var chunks=new HashSet<net.minecraft.world.level.ChunkPos>();
        for(BlockPos pos:BlockPos.betweenClosed(seed.offset(-1,-1,-1),seed.offset(12,12,12)))chunks.add(new net.minecraft.world.level.ChunkPos(pos));
        for(var c:chunks){h.getLevel().getChunk(c.x,c.z);h.getLevel().setChunkForced(c.x,c.z,true);}
        for(BlockPos pos:BlockPos.betweenClosed(seed.offset(-1,-1,-1),seed.offset(12,12,12)))h.getLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),18);
        for(BlockPos pos:BlockPos.betweenClosed(seed,seed.offset(11,11,11)))h.getLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),18);
        p.setPos(seed.getX()+5.5,seed.getY()+5,seed.getZ()-6);p.setXRot(0);p.setYRot(0);
        for(int i=0;i<5;i++)input(p,Intent.ACT);
        h.succeedWhen(()->{
            input(p,Intent.ACT);
            var bodies=h.getLevel().getEntitiesOfClass(dev.chronicle.entity.MatterBody.class,new net.minecraft.world.phys.AABB(seed,seed.offset(12,12,12)).inflate(30));
            var body=bodies.stream().filter(b->b.mass()==1728&&Physics.controlled(b)).findFirst().orElse(null);
            h.assertTrue(body!=null,"Unmodified right click at maximum output must lift all 1,728 cells as a coherent carrier");
            h.assertTrue(h.getLevel().getBlockState(seed).isAir(),"The real source terrain must transfer into the carrier");
            h.assertTrue(body.getDeltaMovement().length()>.1,"The complete carrier must move through the same aim-following force solver");
            Concentration.release(p);body.discard();for(var c:chunks)h.getLevel().setChunkForced(c.x,c.z,false);
        });
    }
}
