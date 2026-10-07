package dev.chronicle.power;

import dev.chronicle.Settings;
import dev.chronicle.entity.MatterBody;
import dev.chronicle.network.Wire;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.*;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;
import static dev.chronicle.power.Intent.*;

/** Input context feeds one force solver; there is no selectable ability state. */
public final class Concentration {
    private static final Map<UUID,Concentration> LIVE=new HashMap<>();
    private final ServerPlayer player;
    private final FlightControl flight=new FlightControl();
    private final LinkedHashMap<UUID,Physics.Hold> held=new LinkedHashMap<>();
    private Intent input=Intent.IDLE;
    private Ward ward;
    private int previous,lastPacket,charge,throwCharge,compressTicks,barrierCharge,armorReady,areaRadius=1,lastStage;
    private int pendingBarrierSteps;
    private boolean chordLatch,gripLatch,primaryGrip,pending,consumed,barrierAdjusted,cutting,rotating;
    private double distance=5,viewImpulse;
    private Vec3 lastLook,trace,cutOrigin;
    private Concentration(ServerPlayer player){this.player=player;areaRadius=Potential.area(player);lastStage=Potential.level(player);}
    public static void accept(ServerPlayer p,Intent intent){
        if(!Potential.acquired(p)||!p.isAlive())return;
        var c=LIVE.computeIfAbsent(p.getUUID(),k->new Concentration(p));c.input=intent;c.lastPacket=p.tickCount;
        if(intent.wheel()==0)return;
        if(intent.has(SNEAK)){Potential.adjustOutput(p,intent.wheel());c.areaRadius=Potential.area(p);c.sync();return;}
        if(intent.has(BARRIER)){if(c.ward!=null)c.ward.resize(intent.wheel());else c.pendingBarrierSteps=Math.max(-3,Math.min(20,c.pendingBarrierSteps+intent.wheel()));c.barrierAdjusted=true;}
        else if(intent.has(GRIP)&&c.held.isEmpty()){c.areaRadius=Math.max(1,Math.min(Potential.area(p),c.areaRadius+intent.wheel()));}
        else if(!c.held.isEmpty()){
            if(intent.has(ATTACK)){c.rotating=true;for(var hold:c.held.values()){if(hold.entity instanceof MatterBody body)body.rotate(intent.wheel());else hold.entity.setYRot(hold.entity.getYRot()+intent.wheel()*15);}}
            else c.distance=Math.max(2,Math.min(Potential.reach(p),c.distance+intent.wheel()*1.5));
        }
    }
    public static void tick(ServerPlayer p){if(!p.isAlive()){release(p);return;}if(Potential.acquired(p))LIVE.computeIfAbsent(p.getUUID(),k->new Concentration(p)).update();}
    private boolean pressed(int bit){return input.has(bit)&&(previous&bit)==0;}
    private boolean released(int bit){return !input.has(bit)&&(previous&bit)!=0;}
    private void update(){
        if(player.tickCount-lastPacket>12)input=Intent.IDLE;
        if(Potential.level(player)!=lastStage){lastStage=Potential.level(player);areaRadius=Potential.area(player);}
        Vec3 look=player.getLookAngle();viewImpulse=lastLook==null?0:Math.min(1,look.subtract(lastLook).length()*2);lastLook=look;
        boolean chord=input.has(GRIP)&&input.has(ACT)&&input.has(SNEAK)&&held.isEmpty()&&!pending;
        if(chord&&!chordLatch){stop();Potential.active(player,!Potential.active(player));chordLatch=true;hint(Potential.active(player)?"Concentration engaged":"Concentration released");sync();}
        if(chordLatch){if(!input.has(GRIP)&&!input.has(ACT))chordLatch=false;previous=input.buttons();return;}
        if(player.tickCount%20==0)Potential.experience(player,Settings.AGE_XP.get()*Settings.LEVEL_RATE.get());
        double before=Potential.strain(player);
        if(Potential.active(player)){
            if(!input.has(ACT)&&!input.has(GRIP))gripLatch=false;
            if(pressed(ACT)){charge=0;consumed=false;cutting=input.has(SPRINT)&&input.has(SNEAK)&&!(player.getMainHandItem().getItem() instanceof BlockItem);cutOrigin=Applications.aim(player).getLocation();trace=cutOrigin;
                if(!held.isEmpty()){for(var hold:held.values())hold.unanchor();primaryGrip=true;consumed=true;}
                else if(!input.has(JUMP)&&!cutting&&!input.has(SNEAK)){Entity target=Applications.target(player);if(target!=null){attach(target);primaryGrip=true;consumed=!held.isEmpty();}}
            }
            if(input.has(ACT))charge=Math.min(200,charge+1);
            if(pressed(GRIP)&&!held.isEmpty()){var target=Applications.target(player);if(target!=null){if(input.has(SNEAK))for(var member:Applications.group(player,target,areaRadius))attach(member);else attach(target);}}
            if(input.has(ACT)&&input.has(SPRINT)&&!cutting&&held.isEmpty()&&player.getMainHandItem().getItem() instanceof BlockItem&&charge%4==0){Applications.build(player,input.has(SNEAK),false,null);consumed=true;}
            if(!gripLatch&&!pending&&held.isEmpty()&&!input.has(JUMP)&&!input.has(ATTACK)&&!cutting){
                if(input.has(GRIP))acquire(input.has(SNEAK));
                else if(input.has(ACT)&&charge>=(input.has(SNEAK)?20:7)&&(!Applications.utility(player)||charge>=20)&&!Applications.ripe(player)&&!(player.getMainHandItem().getItem() instanceof BlockItem&&input.has(SPRINT)))acquire(input.has(SNEAK));
            }
            if(!held.isEmpty()){
                steer();
                boolean compress=input.has(SNEAK)&&(input.has(ATTACK)||(!primaryGrip&&input.has(ACT)));
                if(compress){compressTicks++;if(compressTicks==1)for(var hold:held.values()){if(hold.entity instanceof LivingEntity living)strip(living,false);for(Entity rider:hold.entity.getIndirectPassengers())if(rider instanceof LivingEntity living&&rider!=player)strip(living,false);}if(compressTicks>12&&compressTicks<=Settings.CHOKE_TIME.get())for(var hold:held.values())hold.compress(player,1+Math.min(100,compressTicks)/20.);if(compressTicks==60)for(var hold:held.values())if(hold.entity instanceof LivingEntity living)strip(living,true);}
                else compressTicks=0;
                if(input.has(ATTACK)&&!input.has(SNEAK))throwCharge=Math.min(100,throwCharge+1);
                if(released(ATTACK)&&!input.has(SNEAK)){if(!rotating){if(input.has(SPRINT))placeOrPin();else drop(true);}rotating=false;throwCharge=0;consumed=true;}
                if(released(ACT)&&primaryGrip){if(input.has(SPRINT))placeOrPin();else if(!input.has(GRIP))drop(false);primaryGrip=false;consumed=true;}
                if(released(GRIP)&&!input.has(ACT))drop(false);
            }
            if(input.has(ACT)&&cutting&&charge%4==0){Applications.edge(player,input.has(ATTACK)?Intent.Edge.PLANE:Intent.Edge.TRACE,cutOrigin,trace);trace=Applications.aim(player).getLocation();consumed=true;}
            if(released(ACT)){if(cutting)Applications.edge(player,input.has(ATTACK)?Intent.Edge.PLANE:viewImpulse>.4?Intent.Edge.SWEEP:charge<7?Intent.Edge.PIERCE:Intent.Edge.LINE,cutOrigin,trace);else if(!consumed&&!pending)releaseOutput();charge=0;cutting=false;}
            if(input.has(BARRIER)){barrierCharge++;if(ward!=null&&barrierCharge>=8){Entity target=Applications.target(player);ward.reposition(target==null?Applications.aim(player).getLocation():null,target,player.getLookAngle());ward.invest(barrierCharge);barrierAdjusted=true;}}
            if(released(BARRIER)){if(ward!=null){if(!barrierAdjusted){ward.close(player,true);ward=null;}}else raiseBarrier();barrierCharge=0;barrierAdjusted=false;}
            if(ward!=null&&!ward.tick(player,input.has(BARRIER)&&input.has(SNEAK)&&input.has(ATTACK))){ward.close(player,false);ward=null;sound(SoundEvents.AMETHYST_BLOCK_BREAK,.45f,.6f);}
            flight.tick(player,input);
            if(Potential.level(player)>=Settings.HEAL_LEVEL.get()&&player.tickCount%20==0&&player.getHealth()<player.getMaxHealth()&&Potential.strain(player)<Potential.threshold(player)*.65){player.heal((float)(Settings.HEAL_RATE.get()*Potential.control(player)*Potential.output(player)));Potential.spend(player,5);}
        }
        if(Potential.strain(player)<=before)Potential.recover(player);
        if(player.tickCount%3==0)sync();previous=input.buttons();
    }
    private void acquire(boolean area){
        Entity target=Applications.target(player);
        if(target!=null){if(area)for(Entity entity:Applications.group(player,target,areaRadius))attach(entity);else attach(target);primaryGrip=input.has(ACT);consumed=!held.isEmpty();return;}
        if(Applications.ripe(player)){Applications.tend(player,area);consumed=true;gripLatch=true;return;}
        if(Applications.aim(player).getType()!=HitResult.Type.BLOCK)return;
        pending=true;consumed=true;primaryGrip=input.has(ACT);
        Applications.liftAsync(player,area?Math.min(areaRadius,Potential.area(player)):0,body->{pending=false;if(body==null){gripLatch=true;return;}if(LIVE.get(player.getUUID())==this&&Potential.active(player)&&(input.has(GRIP)||input.has(ACT))){attach(body);distance=Math.max(3,Math.min(Potential.reach(player),Math.sqrt(body.width()*body.width()+body.depth()*body.depth())*.6+3));if(input.has(JUMP)&&!flight.active()){player.startRiding(body,true);}}else body.release();});
    }
    private void attach(Entity entity){if(held.size()>=Potential.targets(player))return;var hold=Physics.take(player,entity);if(hold!=null){held.put(hold.entity.getUUID(),hold);Potential.spend(player,.3+Math.sqrt(Physics.mass(entity))*.08);if(held.size()==1)sound(SoundEvents.AMETHYST_BLOCK_CHIME,.12f,1.3f);}}
    private void steer(){
        Vec3 destination=player.getEyePosition().add(player.getLookAngle().scale(distance));
        if(!flight.active()){if(input.has(JUMP))destination=destination.add(0,1,0);if(input.has(SPRINT))destination=destination.add(0,-.6,0);}
        Iterator<Physics.Hold> iterator=held.values().iterator();int index=0;
        while(iterator.hasNext()){var hold=iterator.next();if(!hold.entity.isAlive()){hold.close();iterator.remove();continue;}double radius=.75+Math.cbrt(index)*.75;Vec3 offset=held.size()>1?new Vec3(Math.cos(index*2.399)*radius,((index%7)-3)*.35,Math.sin(index++*2.399)*radius):Vec3.ZERO;
            if(!hold.steer(player,destination.add(offset),compressTicks>0)){hold.close();iterator.remove();continue;}Potential.spend(player,(.025+Math.sqrt(Physics.mass(hold.entity))*.016)/Math.sqrt(Potential.scale(player)));}
    }
    private void releaseOutput(){
        boolean sneak=input.has(SNEAK);
        if(input.has(JUMP)){if(sneak)radar();else if(unlock(Settings.FLIGHT_LEVEL.get()))flight.toggle(player);sync();return;}
        if(cutting){Applications.edge(player,input.has(GRIP)?Intent.Edge.PLANE:charge<7?Intent.Edge.PIERCE:Intent.Edge.LINE,cutOrigin,trace);return;}
        if(charge<7&&Applications.ripe(player)){Applications.tend(player,sneak);return;}
        if(player.getMainHandItem().getItem() instanceof BlockItem&&input.has(SPRINT)){Applications.build(player,sneak,false,null);return;}
        if(charge<7&&!sneak&&Applications.interact(player))return;
        boolean radial=charge>=35&&Potential.level(player)>=Settings.BURST_LEVEL.get();double output=.35+charge/20.;
        Applications.pressure(player,sneak,output,radial);
        if(radial)Applications.burstTerrain(player,charge);
        effect(radial?Wire.Effect.EXPLOSION:Wire.Effect.WAVE,radial?player.position().add(0,1,0):player.getEyePosition().add(player.getLookAngle().scale(2)),Math.min(Potential.reach(player),3+charge/4.));
        sound(radial?SoundEvents.WARDEN_SONIC_BOOM:SoundEvents.AMETHYST_BLOCK_RESONATE,radial?.55f:.2f,radial?.7f:1.1f);
    }
    private void raiseBarrier(){if(!unlock(Settings.FIELD_LEVEL.get()))return;boolean aimed=barrierCharge>=8;Entity target=aimed?Applications.target(player):null;Vec3 point=aimed&&target==null?Applications.aim(player).getLocation():null;Ward.Shape shape=input.has(SNEAK)?Ward.Shape.PLANE:input.has(SPRINT)?Ward.Shape.DOME:Ward.Shape.SPHERE;ward=new Ward(player,point,target,shape);ward.invest(barrierCharge);for(int n=0;n<Math.abs(pendingBarrierSteps);n++)ward.resize(Integer.signum(pendingBarrierSteps));pendingBarrierSteps=0;}
    private void radar(){if(!unlock(Settings.SENSE_LEVEL.get()))return;for(LivingEntity entity:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(Potential.reach(player))))if(entity!=player)entity.addEffect(new MobEffectInstance(MobEffects.GLOWING,80,0,false,false));Potential.spend(player,6);}
    private void drop(boolean launch){double amount=.6+throwCharge/25.;for(var hold:held.values()){if(!launch&&hold.anchored())continue;hold.close();if(launch)Physics.launch(player,hold.entity,player.getLookAngle().scale(Math.min(7,(.6+Math.sqrt(Potential.force(player)))*amount*Potential.control(player)+viewImpulse)).add(player.getDeltaMovement().scale(.4)));}held.entrySet().removeIf(row->launch||!row.getValue().anchored());if(launch){Potential.spend(player,3+amount*2);gripLatch=true;sound(SoundEvents.AMETHYST_BLOCK_RESONATE,.22f,.85f);}}
    private void placeOrPin(){var hit=Applications.aim(player);if(hit.getType()!=HitResult.Type.BLOCK){for(var hold:held.values())hold.anchor(hold.entity.getBoundingBox().getCenter());return;}BlockPos target=hit.getBlockPos().relative(hit.getDirection());var iterator=held.values().iterator();while(iterator.hasNext()){var hold=iterator.next();if(hold.entity instanceof MatterBody body){if(body.place(target.offset(-body.width()/2,0,-body.depth()/2),player)){hold.close();iterator.remove();}else {hold.anchor(hold.entity.getBoundingBox().getCenter());hint("Destination occupied");}}else if(hold.entity instanceof ItemEntity item&&Applications.deposit(player,item)){hold.close();iterator.remove();}else hold.anchor(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(hold.entity.getBbWidth()*.6)));}}
    private void strip(LivingEntity target,boolean armor){if(armor&&(Potential.level(player)<8||player.tickCount<armorReady))return;EquipmentSlot slot=target.getMainHandItem().isEmpty()?EquipmentSlot.OFFHAND:EquipmentSlot.MAINHAND;if(armor){slot=EquipmentSlot.MAINHAND;for(var candidate:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})if(!target.getItemBySlot(candidate).isEmpty()){slot=candidate;break;}if(slot==EquipmentSlot.MAINHAND)return;}ItemStack item=target.getItemBySlot(slot);if(item.isEmpty())return;var loose=new ItemEntity(player.level(),target.getX(),target.getEyeY(),target.getZ(),item.copy());if(!player.level().addFreshEntity(loose))return;target.setItemSlot(slot,ItemStack.EMPTY);Physics.impulse(player,loose,player.getEyePosition().subtract(loose.position()).normalize().scale(.6));if(armor)armorReady=player.tickCount+Settings.ARMOR_COOLDOWN.get();Potential.spend(player,armor?18:5);}
    private boolean unlock(int level){if(Potential.level(player)>=level)return true;hint("Requires potential level "+level);return false;}
    private void hint(String text){if(Settings.HINTS.get())player.displayClientMessage(Component.literal(text),true);}
    private void sound(net.minecraft.sounds.SoundEvent sound,float volume,float pitch){if(Settings.SOUNDS.get())player.level().playSound(null,player.blockPosition(),sound,SoundSource.PLAYERS,volume,pitch);}
    private void effect(int kind,Vec3 center,double radius){if(Settings.GEOMETRY_EFFECTS.get())Wire.CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(center.x,center.y,center.z,96,player.level().dimension())),new Wire.Effect(kind,center,radius,Potential.color(player)));}
    private void stop(){for(var hold:held.values())hold.close();held.clear();flight.close(player);if(ward!=null){ward.close(player,false);ward=null;}primaryGrip=false;}
    private void sync(){Wire.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->player),new Wire.View(player.getId(),Potential.acquired(player),Potential.active(player),Settings.AURA.get()?Potential.color(player):-1,Settings.GEOMETRY_EFFECTS.get(),ward==null?null:ward.center(player),ward==null?0:ward.radius(player),ward!=null&&ward.plane(),ward==null?0:ward.integrity(player),ward==null?new Vec3(0,0,1):ward.normal(),ward==null?0:ward.shape().ordinal(),ward==null?null:ward.impact(),ward==null?100:ward.impactAge(player),flight.active(),!held.isEmpty(),(float)Potential.output(player)));}
    public static void synchronize(ServerPlayer p){LIVE.computeIfAbsent(p.getUUID(),k->new Concentration(p)).sync();}
    public static void release(ServerPlayer p){var c=LIVE.remove(p.getUUID());if(c!=null){c.stop();c.sync();}}
    public static boolean shield(LivingEntity victim,float amount){return shield(victim,amount,null);}
    public static boolean shield(LivingEntity victim,float amount,Entity attacker){for(var c:LIVE.values())if(c.player!=attacker&&Potential.active(c.player)&&c.ward!=null&&c.player.level()==victim.level()&&c.ward.protects(c.player,victim,attacker)){Vec3 impact=attacker==null?victim.position():c.ward.crossing(c.player,Ward.sourcePoint(attacker),victim.getBoundingBox().getCenter()).orElse(victim.position());if(c.ward.absorb(c.player,amount,impact))return true;}return false;}
    public static void reset(){for(var c:LIVE.values())c.stop();LIVE.clear();Physics.reset();}
}
