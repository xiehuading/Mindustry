package io.anuke.mindustry;

import io.anuke.arc.*;
import io.anuke.arc.assets.*;
import io.anuke.arc.graphics.*;
import io.anuke.arc.graphics.g2d.*;
import io.anuke.arc.math.*;
import io.anuke.arc.scene.ui.layout.*;
import io.anuke.arc.util.*;
import io.anuke.mindustry.core.*;
import io.anuke.mindustry.game.EventType.*;
import io.anuke.mindustry.gen.*;
import io.anuke.mindustry.graphics.*;

import static io.anuke.arc.Core.*;
import static io.anuke.mindustry.Vars.*;

public class ClientLauncher extends ApplicationCore{
    private static final int loadingFPS = 20;

    private float smoothProgress, smoothTime;
    private long lastTime;
    private long beginTime;
    private boolean finished = false;

    @Override
    public void setup(){
        Log.setUseColors(false);
        beginTime = Time.millis();

        Time.setDeltaProvider(() -> {
            float result = Core.graphics.getDeltaTime() * 60f;
            return (Float.isNaN(result) || Float.isInfinite(result)) ? 1f : Mathf.clamp(result, 0.0001f, 60f / 10f);
        });

        batch = new SpriteBatch();
        assets = new AssetManager();
        atlas = TextureAtlas.blankAtlas();

        Time.mark();
        UI.loadDefaultFont();
        UI.loadSystemCursors();
        Log.info("UI init: {0}", Time.elapsed());

        assets.load(new Vars());
        assets.load(new AssetDescriptor<>("sprites/sprites.atlas", TextureAtlas.class)).loaded = t -> atlas = (TextureAtlas)t;

        Musics.load();
        Sounds.load();

        add(logic = new Logic());
        add(control = new Control());
        add(renderer = new Renderer());
        add(ui = new UI());
        add(netServer = new NetServer());
        add(netClient = new NetClient());

        assets.loadRun("Content", ContentLoader.class, () -> {
            content.init();
            content.load();
        });
    }

    private void post(){
        for(ApplicationListener listener : modules){
            listener.init();
        }
    }

    @Override
    public void add(ApplicationListener module){
        super.add(module);

        //autoload modules when necessary
        if(module instanceof Loadable){
            assets.load((Loadable)module);
        }
    }

    @Override
    public void resize(int width, int height){
        super.resize(width, height);

        if(!assets.isFinished()){
            Draw.proj().setOrtho(0, 0, width, height);
        }
    }

    @Override
    public void update(){
        if(!assets.update(1000 / loadingFPS)){
            drawLoading();
        }else{
            if(!finished){
                Log.info("Time to load: {0}", Time.timeSinceMillis(beginTime));
                post();
                finished = true;
                Events.fire(new ClientLoadEvent());
            }

            super.update();
        }

        int targetfps = Core.settings.getInt("fpscap", 120);

        if(targetfps > 0 && targetfps <= 240){
            long target = (1000 * 1000000) / targetfps; //target in nanos
            long elapsed = Time.timeSinceNanos(lastTime);
            if(elapsed < target){
                try{
                    Thread.sleep((target - elapsed) / 1000000, (int)((target - elapsed) % 1000000));
                }catch(InterruptedException ignored){
                    //ignore
                }
            }
        }

        lastTime = Time.nanos();
    }

    @Override
    public void init(){
        setup();
    }

    @Override
    public void resume(){
        if(finished){
            super.resume();
        }
    }

    void drawLoading(){
        smoothProgress = Mathf.lerpDelta(smoothProgress, assets.getProgress(), 0.1f);
        smoothTime += Time.delta();

        Core.graphics.clear(Color.BLACK);
        Draw.proj().setOrtho(0, 0, Core.graphics.getWidth(), Core.graphics.getHeight());
        float height = UnitScl.dp.scl(50f);

        Draw.color(Pal.darkerGray);
        Fill.rect(graphics.getWidth()/2f, graphics.getHeight()/2f, graphics.getWidth(), height);
        Draw.color(Pal.accent, Color.WHITE, Mathf.absin(smoothTime, 5f, 1f) * 0.5f);
        Fill.crect(0, graphics.getHeight()/2f - height/2f, graphics.getWidth() * smoothProgress, height);

        if(assets.isLoaded("outline")){
            BitmapFont font = assets.get("outline");
            font.draw((int)(assets.getProgress() * 100) + "%", graphics.getWidth() / 2f, graphics.getHeight() / 2f, Align.center);

            if(assets.getCurrentLoading() != null){
                String name = assets.getCurrentLoading().fileName.toLowerCase();
                String key = name.contains("msav") ? "map" : name.contains("ogg") || name.contains("mp3") ? "sound" : name.contains("png") ? "image" : "content";
                font.draw(bundle.get("load." + key, ""), graphics.getWidth() / 2f, graphics.getHeight() / 2f - height / 2f - 10f, Align.center);
            }
        }
        Draw.flush();
    }
}