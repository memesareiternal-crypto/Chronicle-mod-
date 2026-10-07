package dev.chronicle.power;

import dev.chronicle.Settings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Shared geometry is used for rendering, projectile crossing, and damage interception. */
public final class Ward {
    public enum Shape { SPHERE, DOME, PLANE }
    private Vec3 anchor,normal,impact;
    private Entity attachment;
    private final Shape shape;
    private final List<Physics.Hold> caught=new ArrayList<>();
    private double health,maximum,scale=1,output=1;
    private int impactTick=-100,scanOffset;
    public Ward(ServerPlayer p,Vec3 anchor,Entity attachment,boolean plane){this(p,anchor,attachment,plane?Shape.PLANE:Shape.SPHERE);}
    public Ward(ServerPlayer p,Vec3 anchor,Entity attachment,Shape shape){this.anchor=anchor;this.attachment=attachment;this.shape=shape;normal=p.getLookAngle().normalize();maximum=(Settings.BARRIER_HEALTH.get()+(Potential.level(p)-1)*Settings.BARRIER_GROWTH.get())*Potential.strength();health=maximum;}
    public static double capacity(ServerPlayer p){return (2+Potential.level(p)*Settings.BULLET_CAPACITY.get())*Potential.scale(p)*Potential.strength()*Potential.control(p)*Potential.output(p);}
    public Vec3 center(ServerPlayer p){Vec3 c=attachment!=null&&attachment.isAlive()?attachment.getBoundingBox().getCenter():anchor==null?p.getBoundingBox().getCenter():anchor;return shape==Shape.PLANE&&anchor==null?c.add(normal.scale(2)):c;}
    public Vec3 normal(){return normal;}
    public Shape shape(){return shape;}
    public boolean plane(){return shape==Shape.PLANE;}
    public Vec3 impact(){return impact;}
    public int impactAge(ServerPlayer p){return p.tickCount-impactTick;}
    public double radius(ServerPlayer p){return Math.min(Settings.BARRIER_MAX_RADIUS.get(),(Settings.FIELD_RADIUS.get()+(Potential.level(p)-1)*.45)*Math.sqrt(Potential.strength())*scale);}
    public float integrity(ServerPlayer p){return (float)Math.max(0,health/Math.max(1,maximum));}
    public void resize(int direction){scale=Math.max(.25,Math.min(6,scale+direction*.25));}
    public void invest(double charge){output=Math.min(4,1+Math.max(0,charge)/40.);}
    public void reposition(Vec3 point,Entity entity,Vec3 facing){anchor=point;attachment=entity;if(facing!=null&&facing.lengthSqr()>.5)normal=facing.normalize();}
    private double threshold(ServerPlayer p){double area=Math.pow(radius(p)/Math.max(1,Settings.FIELD_RADIUS.get()),2);return capacity(p)*output/Math.max(1,Math.sqrt(area));}
    public boolean contains(ServerPlayer p,Vec3 point){Vec3 d=point.subtract(center(p));return !plane()&&d.lengthSqr()<=Math.pow(radius(p),2)&&(shape!=Shape.DOME||d.y>=-.25);}
    public Optional<Vec3> crossing(ServerPlayer p,Vec3 from,Vec3 to){return crossing(shape,center(p),normal,radius(p),from,to);}
    public static Optional<Vec3> crossing(Shape shape,Vec3 center,Vec3 normal,double radius,Vec3 from,Vec3 to){
        Vec3 a=from.subtract(center),d=to.subtract(from);
        if(shape==Shape.PLANE){double denominator=d.dot(normal);if(Math.abs(denominator)<1e-8)return Optional.empty();double t=-a.dot(normal)/denominator;if(t<0||t>1)return Optional.empty();Vec3 point=from.add(d.scale(t));return point.distanceToSqr(center)<=radius*radius?Optional.of(point):Optional.empty();}
        double aa=d.lengthSqr();if(aa<1e-8)return Optional.empty();double bb=2*a.dot(d),cc=a.lengthSqr()-radius*radius,disc=bb*bb-4*aa*cc;if(disc<0)return Optional.empty();
        double root=Math.sqrt(disc);for(double t:new double[]{(-bb-root)/(2*aa),(-bb+root)/(2*aa)})if(t>=0&&t<=1){Vec3 point=from.add(d.scale(t));if(shape!=Shape.DOME||point.y>=center.y-.25)return Optional.of(point);}return Optional.empty();
    }
    public boolean protects(ServerPlayer p,Entity entity){return contains(p,entity.getBoundingBox().getCenter());}
    public boolean protects(ServerPlayer p,Entity entity,Entity attacker){
        Vec3 victim=entity.getBoundingBox().getCenter();if(attacker==null)return !plane()&&contains(p,victim);
        Vec3 source=sourcePoint(attacker);if(!plane()&&contains(p,source))return false;
        return crossing(p,source,victim).isPresent();
    }
    public static Vec3 sourcePoint(Entity attacker){return attacker instanceof Projectile projectile&&projectile.getOwner()!=null?projectile.getOwner().getBoundingBox().getCenter():attacker.getBoundingBox().getCenter();}
    public boolean absorb(ServerPlayer p,float damage){return absorb(p,damage,center(p));}
    public boolean absorb(ServerPlayer p,double damage,Vec3 point){
        if(!Double.isFinite(damage)||damage<0)return false;
        impact=point;impactTick=p.tickCount;
        double limit=threshold(p);health-=damage;
        Potential.spend(p,Math.min(damage,limit)*.35);
        if(Settings.SOUNDS.get()&&p.tickCount%3==0)p.level().playSound(null,point.x,point.y,point.z,SoundEvents.AMETHYST_BLOCK_HIT,SoundSource.PLAYERS,.3f,(float)(.7+integrity(p)*.5));
        return damage<=limit&&health>0;
    }
    public boolean tick(ServerPlayer p,boolean compress){
        if(health<=0||center(p).distanceToSqr(p.position())>Math.pow(Potential.reach(p)*2,2))return false;
        Vec3 center=center(p);double radius=radius(p);
        List<Entity> candidates=p.level().getEntities(p,new AABB(center,center).inflate(radius+12),e->Physics.allowed(p,e)&&!Physics.controlled(e));
        candidates.sort(Comparator.comparingInt(e->e instanceof Projectile?0:1));
        int budget=Math.min(Settings.PROJECTILE_BUDGET.get(),candidates.size());
        for(int n=0;n<budget;n++) {
            Entity entity=candidates.get(n);if(Physics.controlled(entity)||entity==attachment)continue;
            Vec3 current=entity.getBoundingBox().getCenter(),before=current.subtract(entity.getDeltaMovement()),after=current.add(entity.getDeltaMovement());
            var hit=crossing(p,before,after);
            if(entity instanceof Projectile projectile) {
                if(projectile.getOwner()==p||(!plane()&&projectile.getOwner()!=null&&contains(p,projectile.getOwner().getBoundingBox().getCenter()))||hit.isEmpty()||(!plane()&&hit.get().subtract(center).dot(entity.getDeltaMovement())>=0)||caught.size()>=Settings.BARRIER_CAPTURE_LIMIT.get())continue;
                double damage=Physics.damageEstimate(projectile);if(!Double.isFinite(damage)||damage>threshold(p)) {if(hit.isPresent()&&Double.isFinite(damage))absorb(p,damage,hit.get());continue;}
                if(!absorb(p,damage,hit.get()))continue;
                var hold=Physics.take(p,entity);if(hold!=null){Physics.velocity(entity,Vec3.ZERO);caught.add(hold);}
            }else if(entity instanceof LivingEntity living && !(entity instanceof net.minecraft.world.entity.player.Player)) {
                if(!compress&&(hit.isEmpty()||(!plane()&&hit.get().subtract(center).dot(entity.getDeltaMovement())>=0)))continue;
                if(compress&&!contains(p,current))continue;
                double attack=living.getAttribute(Attributes.ATTACK_DAMAGE)==null?1:living.getAttributeValue(Attributes.ATTACK_DAMAGE);
                if(attack>threshold(p)){if(p.tickCount%10==0)absorb(p,attack,current);continue;}
                Vec3 away=plane()?normal.scale(Math.signum(current.subtract(center).dot(normal))):current.subtract(center).normalize();
                Physics.impulse(p,entity,away.scale(compress?-.06:.15+Potential.force(p)*.025));
                if(compress&&p.tickCount%10==0){var hold=Physics.take(p,entity);if(hold!=null){hold.compress(p,output);hold.close();}}
            }
        }
        scanOffset+=Math.max(1,budget);
        Iterator<Physics.Hold> iterator=caught.iterator();int index=0;
        while(iterator.hasNext()) {var hold=iterator.next();double angle=index++*2.399;Vec3 target=center.add(Math.cos(angle)*radius*.65,.3+index*.01,Math.sin(angle)*radius*.65);if(!hold.steer(p,target,false)){hold.close();iterator.remove();} }
        Potential.spend(p,Settings.BARRIER_MAINTENANCE.get()*Math.pow(radius/Math.max(1,Settings.FIELD_RADIUS.get()),2)*output+caught.size()*.025);
        return health>0;
    }
    public int captured(){return caught.size();}
    public void close(ServerPlayer p,boolean redirect){for(var hold:caught){hold.close();if(redirect)Physics.launch(p,hold.entity,p.getLookAngle().scale(Math.min(5,1.5+Potential.force(p)*.3)));}caught.clear();}
}
