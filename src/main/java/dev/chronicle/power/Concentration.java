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

/** Three immediate gestures drive the same grip, flight, impact and protection systems. */
public final class Concentration {
    private static final Map<UUID,Concentration> LIVE=new HashMap<>();
    private final ServerPlayer player;
    private final FlightControl flight=new FlightControl();
    private final LinkedHashMap<UUID,Physics.Hold> held=new LinkedHashMap<>();
    private Intent input=Intent.IDLE;
    private Ward ward;
    private Wire.FlightState sentFlight;
    private int previous,lastPacket,charge,throwCharge,compressTicks,barrierCharge,forceCharge,armorReady;
    private boolean pending,consumed,primaryGrip,orbit,personalProtection,rotating,gripLatch,volleyGesture,forcePull,forceRadial;
    private double distance=5,viewImpulse;
    private Vec3 lastLook,orbitPoint,personalImpact;
    private int personalImpactTick=-100;
    private Concentration(ServerPlayer player){this.player=player;}
    public static void accept(ServerPlayer p,Intent intent){
        if(!Potential.acquired(p)||!p.isAlive())return;
        var c=LIVE.computeIfAbsent(p.getUUID(),k->new Concentration(p));
        c.input=intent;c.lastPacket=p.tickCount;
        if(intent.wheel()==0)return;
        if(intent.has(SNEAK)){Potential.adjustOutput(p,intent.wheel());c.sync();return;}
        if(intent.has(BARRIER)&&c.ward!=null)c.ward.resize(intent.wheel());
        else if(!c.held.isEmpty()){
            if(intent.has(ATTACK)){c.rotating=true;for(var hold:c.held.values()){if(hold.entity instanceof MatterBody body)body.rotate(intent.wheel());else hold.entity.setYRot(hold.entity.getYRot()+intent.wheel()*15);}}
            else c.distance=Math.max(2,Math.min(Potential.reach(p),c.distance+intent.wheel()*2));
        }
    }
    public static void tick(ServerPlayer p){
        if(!p.isAlive()){release(p);return;}
        if(Potential.acquired(p))LIVE.computeIfAbsent(p.getUUID(),k->new Concentration(p)).update();
    }
    private boolean pressed(int bit){return input.has(bit)&&(previous&bit)==0;}
    private boolean released(int bit){return !input.has(bit)&&(previous&bit)!=0;}
    private void update(){
        if(player.tickCount-lastPacket>12)input=Intent.IDLE;
        Vec3 look=player.getLookAngle();viewImpulse=lastLook==null?0:Math.min(1,look.subtract(lastLook).length()*2);lastLook=look;
        if(player.tickCount%20==0)Potential.experience(player,Settings.AGE_XP.get()*Settings.LEVEL_RATE.get());
        if(!Potential.active(player)){previous=input.buttons();return;}
        if(pressed(FORCE)){forceCharge=0;volleyGesture=!held.isEmpty();}
        if(input.has(FORCE)){forcePull=input.has(SNEAK);forceRadial=input.has(SPRINT);}
        if(flight.gesture(player,input,previous))sync();
        if(!input.has(ACT)&&!input.has(ATTACK))gripLatch=false;
        if(pressed(GRIP)){
            if(orbit&&!input.has(ACT)){orbit=false;orbitPoint=null;drop(false);}
            else {
                orbit=true;orbitPoint=input.has(SPRINT)?Applications.aim(player).getLocation():null;
                gather();radar();
            }
        }
        if(input.has(GRIP)&&orbit&&player.tickCount%10==0)gatherEntities();
        if(pressed(ACT)){
            charge=0;consumed=false;primaryGrip=true;
            for(var hold:held.values())hold.unanchor();
            if(!held.isEmpty())consumed=true;
            else if(!input.has(FORCE)){Entity target=Applications.target(player);if(target!=null)acquire();}
        }
        if(input.has(ACT))charge=Math.min(120,charge+1);
        if(!gripLatch&&!pending&&held.isEmpty()&&input.has(ACT)&&!input.has(FORCE)&&charge>=4
            &&(!Applications.utility(player)||charge>=12)&&!Applications.ripe(player)
            &&!(player.getMainHandItem().getItem() instanceof BlockItem&&input.has(SPRINT)))acquire();
        if(input.has(ACT)&&input.has(SPRINT)&&held.isEmpty()&&player.getMainHandItem().getItem() instanceof BlockItem&&charge%4==0){
            Applications.build(player,input.has(SNEAK),false,null);consumed=true;
        }
        if(!held.isEmpty()){
            boolean compress=input.has(SNEAK)&&input.has(ATTACK);
            compressTicks=compress?compressTicks+1:0;
            if(compressTicks==1)for(var hold:held.values()){
                if(hold.entity instanceof LivingEntity living)strip(living,false);
                for(Entity rider:hold.entity.getIndirectPassengers())if(rider instanceof LivingEntity living&&rider!=player)strip(living,false);
            }
            if(compressTicks>12)for(var hold:held.values())hold.compress(player,1+Math.min(100,compressTicks)/20.);
            if(compressTicks==60)for(var hold:held.values())if(hold.entity instanceof LivingEntity living)strip(living,true);
            steer();
            if(input.has(ATTACK)&&!input.has(SNEAK))throwCharge=Math.min(100,throwCharge+1);
            if(released(ATTACK)&&!input.has(SNEAK)){if(!rotating){if(input.has(SPRINT))placeOrPin();else drop(true);}rotating=false;throwCharge=0;consumed=true;}
            if(input.has(FORCE)){
                volleyGesture=true;
                if(input.has(SNEAK)&&pressed(FORCE))drop(true);
                else if(player.tickCount%3==0)launchOne();
                consumed=true;
            }
        } else {
            if(pressed(FORCE))forceCharge=0;
            if(input.has(FORCE)&&!volleyGesture){
                forceCharge=Math.min(120,forceCharge+1);
                if(input.has(ATTACK)&&forceCharge%4==0){Applications.edge(player,viewImpulse>.35?Edge.PLANE:Edge.PIERCE,null,null);consumed=true;}
            }
            if(released(FORCE)&&!volleyGesture&&!input.has(ATTACK)){
                boolean radial=forceRadial&&forceCharge>=15;
                double amount=.8+forceCharge/10.;
                Applications.pressure(player,forcePull,amount,radial);
                if(!radial)Applications.terrainForce(player,forcePull,forceCharge,body->{
                    if(LIVE.get(player.getUUID())!=this||!Potential.active(player)){body.release();return;}
                    attach(body);var hold=held.get(body.getUUID());
                    if(hold!=null)hold.anchor(body.getBoundingBox().getCenter().add(0,Math.max(3,Potential.area(player)*.5),0));else body.release();
                });
                if(radial&&Potential.level(player)>=Settings.BURST_LEVEL.get())Applications.burstTerrain(player,forceCharge);
                effect(radial?Wire.Effect.EXPLOSION:Wire.Effect.WAVE,radial?player.position().add(0,1,0):Applications.aim(player).getLocation(),Math.min(Potential.reach(player),4+forceCharge*.4));
                sound(radial?SoundEvents.WARDEN_SONIC_BOOM:SoundEvents.AMETHYST_BLOCK_RESONATE,radial?.45f:.18f,.9f);
            }
        }
        if(released(FORCE))volleyGesture=false;
        if(released(ACT)){
            if(primaryGrip&&!held.isEmpty()){
                if(input.has(SPRINT))placeOrPin();else if(!orbit)drop(false);
                consumed=true;
            }
            if(!consumed&&!pending&&!input.has(FORCE)){
                if(Applications.ripe(player))Applications.tend(player,Potential.area(player)>0);
                else Applications.interact(player);
            }
            primaryGrip=false;charge=0;
        }
        if(pressed(BARRIER)){
            personalProtection=!personalProtection;barrierCharge=0;
        }
        if(input.has(BARRIER))barrierCharge++;
        if(released(BARRIER)){
            if(barrierCharge>=10&&Potential.level(player)>=Settings.FIELD_LEVEL.get()){
                if(ward!=null)ward.close(player,false);
                Entity target=Applications.target(player);Vec3 point=target==null?Applications.aim(player).getLocation():null;
                Ward.Shape shape=input.has(SNEAK)?Ward.Shape.PLANE:input.has(SPRINT)?Ward.Shape.DOME:Ward.Shape.SPHERE;
                ward=new Ward(player,point,target,shape);ward.invest(barrierCharge);personalProtection=true;
            }else if(ward!=null){ward.close(player,true);ward=null;}
            barrierCharge=0;
        }
        if(ward!=null&&!ward.tick(player,input.has(BARRIER)&&input.has(SNEAK)&&input.has(ATTACK))){ward.close(player,false);ward=null;}
        interceptDebris();
        flight.tick(player,input);
        if(flight.active()&&player.getDeltaMovement().length()>2.5&&player.tickCount%15==0)effect(Wire.Effect.WAVE,player.position(),2*Potential.output(player));
        if(Potential.level(player)>=Settings.HEAL_LEVEL.get()&&player.tickCount%20==0&&player.getHealth()<player.getMaxHealth()){
            player.heal((float)(Settings.HEAL_RATE.get()*Potential.output(player)));Potential.practice(player,5);
        }
        if(player.tickCount%3==0)sync();previous=input.buttons();
    }
    private void acquire(){
        Entity target=Applications.target(player);
        if(target!=null){
            for(Entity entity:Applications.group(player,target,Math.max(1,Potential.area(player))))attach(entity);
            distance=Math.max(3,Math.min(Potential.reach(player),target.distanceTo(player)));
            consumed=!held.isEmpty();return;
        }
        if(Applications.aim(player).getType()!=HitResult.Type.BLOCK)return;
        pending=true;consumed=true;
        Applications.liftAsync(player,Potential.area(player),body->{
            pending=false;if(body==null){gripLatch=true;return;}
            if(LIVE.get(player.getUUID())==this&&Potential.active(player)&&(input.has(ACT)||orbit)){
                attach(body);distance=Math.max(4,Math.min(Potential.reach(player),Math.max(player.distanceTo(body),Math.hypot(body.width(),body.depth())*.6+3)));
            }else body.release();
        });
    }
    private void gatherEntities(){
        Vec3 center=orbitPoint==null?player.position():orbitPoint;
        for(Entity entity:Applications.nearby(player,center,Math.max(3,Potential.area(player))))attach(entity);
    }
    private void gather(){
        gatherEntities();
        if(!pending&&Potential.area(player)>0){
            pending=true;
            Applications.debris(player,orbitPoint==null?player.position():orbitPoint,body->{
                if(body!=null){if(LIVE.get(player.getUUID())==this&&orbit&&Potential.active(player))attach(body);else body.release();}
            },()->pending=false);
        }
    }
    private void attach(Entity entity){
        if(held.size()>=Potential.targets(player))return;
        var hold=Physics.take(player,entity);
        if(hold!=null){held.put(hold.entity.getUUID(),hold);Potential.practice(player,.3+Math.sqrt(Physics.mass(entity))*.08);}
    }
    private void steer(){
        boolean circling=orbit&&!input.has(ACT);
        Vec3 destination=circling?(orbitPoint==null?player.position().add(0,2,0):orbitPoint):player.getEyePosition().add(player.getLookAngle().scale(distance));
        var iterator=held.values().iterator();int index=0;
        while(iterator.hasNext()){
            var hold=iterator.next();if(!hold.entity.isAlive()){hold.close();iterator.remove();continue;}
            double size=hold.entity.getBbWidth(),radius=circling?3+Math.cbrt(held.size())*.7+size*.5:.7+Math.cbrt(index)*1.2+size*.3;
            double angle=index*2.399+(circling?player.tickCount*.04:0);
            Vec3 offset=circling||held.size()>1?new Vec3(Math.cos(angle)*radius,(index%5)*.7,Math.sin(angle)*radius):Vec3.ZERO;index++;
            if(!hold.steer(player,destination.add(offset),compressTicks>0)){hold.close();iterator.remove();continue;}
            Potential.practice(player,(.025+Math.sqrt(Physics.mass(hold.entity))*.016)/Math.sqrt(Potential.scale(player)));
        }
    }
    private void interceptDebris(){
        if(!orbit||held.isEmpty()||player.tickCount%2!=0)return;
        Vec3 center=orbitPoint==null?player.position():orbitPoint;
        int budget=Settings.PROJECTILE_BUDGET.get();
        for(var projectile:player.level().getEntitiesOfClass(net.minecraft.world.entity.projectile.Projectile.class,new AABB(center,center).inflate(Math.max(12,Potential.area(player))))){
            if(budget--<=0)break;
            if(projectile.getOwner()==player||Physics.controlled(projectile))continue;
            Vec3 from=projectile.position(),to=from.add(projectile.getDeltaMovement().scale(2));
            for(var hold:held.values())if(hold.entity instanceof MatterBody body&&!body.transferring()&&body.getBoundingBox().clip(from,to).isPresent()){
                double damage=Physics.damageEstimate(projectile);
                if(Double.isFinite(damage)&&damage<=body.physicalMass()*2){
                    Physics.launch(player,projectile,projectile.getDeltaMovement().scale(-.6).add(0,.4,0));break;
                }
            }
        }
    }
    private Vec3 launchVelocity(Entity entity,double charge,Vec3 aim){
        double inertia=Math.max(1,Math.pow(Physics.mass(entity),.075));
        double speed=Math.min(7,(1.4+Math.sqrt(Potential.force(player))*2)*charge/inertia+viewImpulse);
        Vec3 delta=aim.subtract(entity.getBoundingBox().getCenter());
        Vec3 direction=delta.lengthSqr()<1?player.getLookAngle():delta.normalize();
        return direction.scale(speed).add(player.getDeltaMovement().scale(.35));
    }
    private void launchOne(){
        var iterator=held.values().iterator();if(!iterator.hasNext())return;var hold=iterator.next();
        hold.close();iterator.remove();Physics.launch(player,hold.entity,launchVelocity(hold.entity,1.4,Applications.aim(player).getLocation()));Potential.practice(player,2);
    }
    private void drop(boolean launch){
        double amount=.7+throwCharge/20.;Vec3 aim=Applications.aim(player).getLocation();
        for(var hold:held.values()){
            if(!launch&&hold.anchored())continue;
            hold.close();if(launch)Physics.launch(player,hold.entity,launchVelocity(hold.entity,amount,aim));
        }
        held.entrySet().removeIf(row->launch||!row.getValue().anchored());
        if(launch){orbit=false;orbitPoint=null;gripLatch=true;Potential.practice(player,3+amount*2);sound(SoundEvents.AMETHYST_BLOCK_RESONATE,.22f,.85f);}
    }
    private void placeOrPin(){var hit=Applications.aim(player);if(hit.getType()!=HitResult.Type.BLOCK){for(var hold:held.values())hold.anchor(hold.entity.getBoundingBox().getCenter());return;}BlockPos target=hit.getBlockPos().relative(hit.getDirection());var iterator=held.values().iterator();while(iterator.hasNext()){var hold=iterator.next();if(hold.entity instanceof MatterBody body){if(body.place(target.offset(-body.width()/2,0,-body.depth()/2),player)){hold.close();iterator.remove();}else {hold.anchor(hold.entity.getBoundingBox().getCenter());hint("Destination occupied");}}else if(hold.entity instanceof ItemEntity item&&Applications.deposit(player,item)){hold.close();iterator.remove();}else hold.anchor(hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(hold.entity.getBbWidth()*.6)));}}
    private void strip(LivingEntity target,boolean armor){if(armor&&(Potential.level(player)<8||player.tickCount<armorReady))return;EquipmentSlot slot=target.getMainHandItem().isEmpty()?EquipmentSlot.OFFHAND:EquipmentSlot.MAINHAND;if(armor){slot=EquipmentSlot.MAINHAND;for(var candidate:new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET})if(!target.getItemBySlot(candidate).isEmpty()){slot=candidate;break;}if(slot==EquipmentSlot.MAINHAND)return;}ItemStack item=target.getItemBySlot(slot);if(item.isEmpty())return;var loose=new ItemEntity(player.level(),target.getX(),target.getEyeY(),target.getZ(),item.copy());if(!player.level().addFreshEntity(loose))return;target.setItemSlot(slot,ItemStack.EMPTY);Physics.impulse(player,loose,player.getEyePosition().subtract(loose.position()).normalize().scale(.6));if(armor)armorReady=player.tickCount+Settings.ARMOR_COOLDOWN.get();Potential.practice(player,armor?18:5);}

    private void radar(){
        if(Potential.level(player)<Settings.SENSE_LEVEL.get())return;
        for(LivingEntity entity:player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(Math.min(64,Potential.reach(player)))))if(entity!=player)entity.addEffect(new MobEffectInstance(MobEffects.GLOWING,60,0,false,false));
    }
    private void hint(String text){if(Settings.HINTS.get())player.displayClientMessage(Component.literal(text),true);}
    private void sound(net.minecraft.sounds.SoundEvent sound,float volume,float pitch){if(Settings.SOUNDS.get())player.level().playSound(null,player.blockPosition(),sound,SoundSource.PLAYERS,volume,pitch);}
    private void effect(int kind,Vec3 center,double radius){if(Settings.GEOMETRY_EFFECTS.get())Wire.CHANNEL.send(PacketDistributor.NEAR.with(()->new PacketDistributor.TargetPoint(center.x,center.y,center.z,128,player.level().dimension())),new Wire.Effect(kind,center,radius,Potential.color(player)));}
    private void stop(){
        for(var hold:held.values())hold.close();held.clear();flight.close(player);
        if(ward!=null){ward.close(player,false);ward=null;}orbit=false;personalProtection=false;primaryGrip=false;
    }
    private void sync(){
        boolean personal=personalProtection&&Potential.active(player);
        Vec3 center=ward==null?(personal?player.getBoundingBox().getCenter():null):ward.center(player);
        double radius=ward==null?(personal?1.15:0):ward.radius(player);
        Wire.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->player),new Wire.View(player.getId(),Potential.acquired(player),Potential.active(player),Settings.AURA.get()?Potential.color(player):-1,Settings.GEOMETRY_EFFECTS.get(),center,radius,ward!=null&&ward.plane(),ward==null?(personal?1:0):ward.integrity(player),ward==null?new Vec3(0,0,1):ward.normal(),ward==null?0:ward.shape().ordinal(),ward==null?personalImpact:ward.impact(),ward==null?player.tickCount-personalImpactTick:ward.impactAge(player),flight.active(),!held.isEmpty(),(float)Potential.output(player)));
        var state=new Wire.FlightState(flight.active(),FlightControl.speed(player),FlightControl.acceleration(player),Settings.FLIGHT_DRAG.get(),flight.originalGravity());
        if(!state.equals(sentFlight)){sentFlight=state;Wire.CHANNEL.send(PacketDistributor.PLAYER.with(()->player),state);}
    }
    public static void toggle(ServerPlayer p){var c=LIVE.computeIfAbsent(p.getUUID(),k->new Concentration(p));c.stop();Potential.active(p,!Potential.active(p));c.sync();}
    public static void synchronize(ServerPlayer p){LIVE.computeIfAbsent(p.getUUID(),k->new Concentration(p)).sync();}
    public static void release(ServerPlayer p){var c=LIVE.remove(p.getUUID());if(c!=null){c.stop();c.sync();}}
    public static boolean protectedPersonally(ServerPlayer p){var c=LIVE.get(p.getUUID());return c!=null&&c.personalProtection&&Potential.active(p);}
    public static void defensiveImpact(ServerPlayer p,Entity attacker){var c=LIVE.get(p.getUUID());if(c==null)return;Vec3 direction=attacker==null?p.getLookAngle():attacker.position().subtract(p.getBoundingBox().getCenter()).normalize();c.personalImpact=p.getBoundingBox().getCenter().add(direction.scale(1.15));c.personalImpactTick=p.tickCount;}
    public static boolean shield(LivingEntity victim,float amount){return shield(victim,amount,null);}
    public static boolean shield(LivingEntity victim,float amount,Entity attacker){
        for(var c:LIVE.values())if(c.orbit&&Potential.active(c.player)&&c.player.level()==victim.level()&&attacker!=null&&c.player!=attacker){
            Vec3 from=Ward.sourcePoint(attacker),to=victim.getBoundingBox().getCenter();
            for(var hold:c.held.values())if(hold.entity instanceof MatterBody body&&!body.transferring()&&amount<=body.physicalMass()*2&&body.getBoundingBox().clip(from,to).isPresent())return true;
        }
        for(var c:LIVE.values())if(c.player!=attacker&&Potential.active(c.player)&&c.ward!=null&&c.player.level()==victim.level()&&c.ward.protects(c.player,victim,attacker)){
            Vec3 impact=attacker==null?victim.position():c.ward.crossing(c.player,Ward.sourcePoint(attacker),victim.getBoundingBox().getCenter()).orElse(victim.position());
            if(c.ward.absorb(c.player,amount,impact))return true;
        }
        return false;
    }
    public static void suppress(net.minecraftforge.event.level.ExplosionEvent.Detonate e){
        for(var c:LIVE.values())if(Potential.active(c.player)&&c.ward!=null&&c.player.level()==e.getLevel()){
            Vec3 blast=e.getExplosion().getPosition();
            boolean intersects=e.getAffectedBlocks().stream().anyMatch(pos->c.ward.crossing(c.player,blast,Vec3.atCenterOf(pos)).isPresent())||e.getAffectedEntities().stream().anyMatch(entity->c.ward.crossing(c.player,blast,entity.getBoundingBox().getCenter()).isPresent());
            if(!intersects)continue;
            double strength=4+e.getAffectedBlocks().size()*.125+e.getAffectedEntities().size()*4.;
            if(c.ward.absorb(c.player,strength,blast)){
                e.getAffectedBlocks().removeIf(pos->c.ward.crossing(c.player,blast,Vec3.atCenterOf(pos)).isPresent());
                e.getAffectedEntities().removeIf(entity->c.ward.crossing(c.player,blast,entity.getBoundingBox().getCenter()).isPresent());
            }
        }
    }
    public static void reset(){for(var c:LIVE.values())c.stop();LIVE.clear();Physics.reset();}
}
