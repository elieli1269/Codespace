package com.elieli1269.europeanfrontline;

import android.app.Activity;
import android.os.Bundle;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        setContentView(new WarView(this));
    }

    static final class Region {
        String id, name, owner, city, countryCode="";
        int x, y, infantry, tanks, rockets, fort;
        float cityX, cityY;
        boolean general, capital=false;
        String[] links;
        final ArrayList<Path> mapPaths = new ArrayList<>();
        final ArrayList<ArrayList<float[]>> mapRings = new ArrayList<>();
        Region(String id, String name, String owner, int x, int y, int inf, int tanks,
               int rockets, int fort, boolean general, String city, String... links) {
            this.id=id; this.name=name; this.owner=owner; this.x=x; this.y=y;
            this.infantry=inf; this.tanks=tanks; this.rockets=rockets; this.fort=fort;
            this.general=general; this.city=city; this.links=links;
            this.cityX=x; this.cityY=y;
        }
        int strength() { return infantry + tanks*2 + rockets*2 + (general?3:0); }
        int total() { return infantry+tanks+rockets; }
    }

    final class WarView extends View {
        static final int W=1200, H=720, MAP_RIGHT=860;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final List<Region> regions = new ArrayList<>();
        final Map<String,Region> byId = new HashMap<>();
        final Random random = new Random();
        final SharedPreferences prefs;
        String selectedId="paris", targetId=null;
        String[] months={"JANVIER","FÉVRIER","MARS","AVRIL","MAI","JUIN","JUILLET","AOÛT","SEPTEMBRE","OCTOBRE","NOVEMBRE","DÉCEMBRE"};
        int month=8, year=1939, turn=1, supplies=130, manpower=90;
        String log1="La campagne commence. Défendez la France.";
        String log2="Choisissez une région bleue, puis une région voisine.";
        String log3="Les autres pays sont commandés par l'IA.";
        float scaleX=1, scaleY=1;
        boolean gameOver=false;
        String endMessage="";
        final int[] palette={0xFF547FA8,0xFF747E88,0xFFB94D4D,0xFF4E8A70,0xFFB49A61,
            0xFF8A6FA8,0xFF4B9B9B,0xFFCE824F,0xFF6F9BC1,0xFF9B7C63,0xFF8EA45B,
            0xFFB45F7C,0xFF687CC3,0xFF9B9B9B,0xFF3D7D68,0xFFC5A26E,0xFF8B5D50,
            0xFF628C9A,0xFFB88CBB,0xFF6D8B55,0xFFB6A77C,0xFF7886A8};

        WarView(Context c) {
            super(c);
            prefs=getSharedPreferences("thewargame-save", Context.MODE_PRIVATE);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            initMap();
            loadSave();
        }

        void add(String id,String name,String owner,int x,int y,int inf,int tanks,int rockets,int fort,
                 boolean general,String city,String... links) {
            Region r=new Region(id,name,owner,x,y,inf,tanks,rockets,fort,general,city,links);
            regions.add(r); byId.put(id,r);
        }

        void initMap() {
            if (!loadRealMap()) {
                initSchematicMap();
            }
        }

        boolean loadRealMap() {
            try (InputStream input = getAssets().open("europe_regions.json");
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                JSONObject root = new JSONObject(new String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8));
                JSONArray data = root.getJSONArray("regions");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject item = data.getJSONObject(i);
                    String id = item.getString("id");
                    String name = item.optString("name", "Région");
                    String owner = item.optString("owner", "Autre");
                    String city = item.optString("city", "");
                    JSONArray center = item.getJSONArray("center");
                    int x = Math.round((float) center.getDouble(0));
                    int y = Math.round((float) center.getDouble(1));
                    JSONArray linksJson = item.optJSONArray("links");
                    String[] links = new String[linksJson == null ? 0 : linksJson.length()];
                    if (linksJson != null) {
                        for (int j = 0; j < linksJson.length(); j++) links[j] = linksJson.getString(j);
                    }
                    boolean capital = item.optBoolean("capital", false);
                    Region region = new Region(id, name, owner, x, y,
                        capital ? 5 : 2, capital ? 1 : 0, 0, capital ? 1 : 0,
                        capital, city, links);
                    region.countryCode = item.optString("cc", "");
                    region.capital = capital;
                    JSONArray cityPoint = item.optJSONArray("cityPoint");
                    if (cityPoint != null && cityPoint.length() >= 2) {
                        region.cityX = (float) cityPoint.getDouble(0);
                        region.cityY = (float) cityPoint.getDouble(1);
                    }
                    JSONArray polygons = item.getJSONArray("polygons");
                    for (int j = 0; j < polygons.length(); j++) {
                        JSONArray points = polygons.getJSONArray(j);
                        if (points.length() < 4) continue;
                        Path path = new Path();
                        ArrayList<float[]> ring = new ArrayList<>();
                        for (int k = 0; k < points.length(); k++) {
                            JSONArray xy = points.getJSONArray(k);
                            float px = (float) xy.getDouble(0);
                            float py = (float) xy.getDouble(1);
                            if (k == 0) path.moveTo(px, py); else path.lineTo(px, py);
                            ring.add(new float[]{px, py});
                        }
                        path.close();
                        region.mapPaths.add(path);
                        region.mapRings.add(ring);
                    }
                    if (region.mapPaths.isEmpty()) continue;
                    regions.add(region);
                    byId.put(region.id, region);
                }
                if (regions.size() < 100) {
                    regions.clear(); byId.clear(); return false;
                }
                String firstFrench = null;
                selectedId = null;
                for (Region region : regions) {
                    if (!"FRA".equals(region.countryCode)) continue;
                    if (firstFrench == null) firstFrench = region.id;
                    if ("Paris".equals(region.city)) { selectedId = region.id; break; }
                }
                if (selectedId == null) selectedId = firstFrench;
                if (selectedId == null && !regions.isEmpty()) selectedId = regions.get(0).id;
                log1 = "La carte géographique est chargée.";
                log2 = regions.size() + " régions et subdivisions réelles.";
                log3 = "Sélectionnez une région pour voir ses informations.";
                return true;
            } catch (Exception ignored) {
                regions.clear();
                byId.clear();
                return false;
            }
        }

        void initSchematicMap() {
            add("ireland","Irlande","Irlande",67,174,2,0,0,0,false,"Dublin","london");
            add("scotland","Écosse","Royaume-Uni",110,118,3,1,0,1,false,"","northengland");
            add("northengland","Nord anglais","Royaume-Uni",133,164,3,1,0,1,false,"","scotland","wales","london");
            add("wales","Pays de Galles","Royaume-Uni",104,204,2,0,0,0,false,"","northengland","london");
            add("london","Sud anglais","Royaume-Uni",148,216,4,1,0,2,true,"Londres","northengland","wales","brittany","ireland");
            add("brittany","Bretagne","France",194,277,2,0,0,0,false,"","paris","bordeaux","london","brussels");
            add("paris","Île-de-France","France",248,275,5,2,0,2,true,"Paris","brittany","bordeaux","lyon","alsace","brussels","ruhr");
            add("bordeaux","Aquitaine","France",208,337,3,1,0,0,false,"Bordeaux","paris","lyon","galicia","castile");
            add("lyon","Centre-Est","France",282,326,4,1,0,1,false,"Lyon","paris","bordeaux","marseille","alsace","switzerland","milan");
            add("marseille","Provence","France",284,382,3,1,0,1,false,"Marseille","lyon","milan","rome");
            add("alsace","Alsace","France",321,283,3,1,0,2,false,"Strasbourg","paris","lyon","brussels","ruhr","munich","switzerland");
            add("lisbon","Portugal","Portugal",111,427,3,0,0,1,false,"Lisbonne","galicia","andalusia");
            add("galicia","Galice","Espagne",151,384,2,0,0,0,false,"","lisbon","castile","bordeaux");
            add("castile","Castille","Espagne",194,401,4,1,0,1,true,"Madrid","galicia","catalonia","andalusia","bordeaux");
            add("catalonia","Catalogne","Espagne",241,394,3,1,0,1,false,"Barcelone","castile","lyon");
            add("andalusia","Andalousie","Espagne",174,454,3,0,0,1,false,"Séville","castile","lisbon");
            add("amsterdam","Pays-Bas","Pays-Bas",315,218,2,0,0,1,false,"Amsterdam","brussels","hamburg","ruhr");
            add("brussels","Belgique","Belgique",310,250,2,0,0,1,false,"Bruxelles","amsterdam","luxembourg","ruhr","alsace","paris","brittany");
            add("luxembourg","Luxembourg","Luxembourg",331,271,1,0,0,1,false,"","brussels","ruhr","munich");
            add("ruhr","Rhénanie","Allemagne",357,254,5,3,1,2,true,"Cologne","amsterdam","brussels","luxembourg","hamburg","berlin","munich","alsace","paris");
            add("hamburg","Nord allemand","Allemagne",370,214,4,2,0,2,false,"Hambourg","ruhr","berlin","denmark");
            add("berlin","Brandebourg","Allemagne",414,243,6,3,1,2,true,"Berlin","ruhr","hamburg","munich","eastprussia","polandwest");
            add("munich","Bavière","Allemagne",380,300,4,2,1,2,false,"Munich","ruhr","berlin","alsace","switzerland","austria","czech");
            add("eastprussia","Prusse orientale","Allemagne",470,229,3,2,0,1,false,"Königsberg","berlin","polandwest","lithuania");
            add("denmark","Danemark","Danemark",390,182,2,0,0,1,false,"Copenhague","hamburg","gothenburg");
            add("oslo","Sud de la Norvège","Norvège",350,133,2,0,0,1,false,"Oslo","trondheim","gothenburg");
            add("trondheim","Nord de la Norvège","Norvège",358,91,2,0,0,0,false,"","oslo","northsweden");
            add("gothenburg","Sud de la Suède","Suède",420,145,3,1,0,1,false,"Göteborg","oslo","stockholm","denmark");
            add("stockholm","Centre de la Suède","Suède",465,124,3,1,0,1,true,"Stockholm","gothenburg","northsweden","finlandsouth");
            add("northsweden","Nord de la Suède","Suède",468,78,2,0,0,0,false,"","stockholm","trondheim","finlandnorth");
            add("finlandsouth","Sud de la Finlande","Finlande",530,134,3,1,0,1,false,"Helsinki","stockholm","finlandnorth","leningrad");
            add("finlandnorth","Nord de la Finlande","Finlande",522,80,2,0,0,0,false,"","finlandsouth","northsweden");
            add("switzerland","Suisse","Suisse",324,310,3,0,0,2,false,"Berne","lyon","alsace","munich","milan");
            add("milan","Nord de l'Italie","Italie",345,343,4,2,0,1,true,"Milan","lyon","marseille","switzerland","rome","austria");
            add("rome","Italie centrale","Italie",352,397,4,2,0,2,false,"Rome","milan","marseille","naples");
            add("naples","Sud de l'Italie","Italie",370,448,3,1,0,1,false,"Naples","rome");
            add("austria","Autriche","Autriche",395,324,3,1,0,1,false,"Vienne","munich","milan","czech","hungary","belgrade");
            add("czech","Bohême-Moravie","Tchécoslovaquie",430,292,3,1,0,1,false,"Prague","munich","austria","polandwest","hungary");
            add("polandwest","Ouest de la Pologne","Pologne",474,273,4,1,0,1,true,"Varsovie","berlin","eastprussia","polandsouth","lithuania","czech");
            add("polandsouth","Sud de la Pologne","Pologne",480,312,3,1,0,1,false,"Cracovie","polandwest","czech","hungary","kyiv");
            add("lithuania","Lituanie","Lituanie",524,218,2,0,0,0,false,"Vilnius","eastprussia","latvia","belarus","polandwest");
            add("latvia","Lettonie","Lettonie",541,193,2,0,0,0,false,"Riga","lithuania","estonia","belarus");
            add("estonia","Estonie","Estonie",554,166,2,0,0,0,false,"Tallinn","latvia","leningrad");
            add("leningrad","Région de Léningrad","URSS",614,189,5,2,1,2,false,"Léningrad","estonia","finlandsouth","smolensk");
            add("belarus","Biélorussie","URSS",586,268,4,2,1,1,false,"Minsk","lithuania","latvia","smolensk","kyiv");
            add("smolensk","Russie occidentale","URSS",656,285,5,3,1,1,false,"Smolensk","leningrad","moscow","belarus");
            add("moscow","Région de Moscou","URSS",728,249,7,4,2,3,true,"Moscou","smolensk");
            add("kyiv","Centre de l'Ukraine","URSS",621,333,5,2,1,1,false,"Kiev","belarus","odessa","romania","hungary","polandsouth");
            add("odessa","Sud de l'Ukraine","URSS",646,381,3,2,1,1,false,"Odessa","kyiv","romania");
            add("bucharest","Roumanie","Roumanie",544,348,3,1,0,1,true,"Bucarest","odessa","hungary","bulgaria","belgrade","kyiv");
            add("hungary","Hongrie","Hongrie",484,335,3,1,0,1,false,"Budapest","czech","austria","polandsouth","kyiv","bucharest","belgrade");
            add("belgrade","Yougoslavie","Yougoslavie",440,365,3,1,0,1,false,"Belgrade","hungary","austria","bucharest","sofia");
            add("sofia","Bulgarie","Bulgarie",530,393,3,1,0,1,false,"Sofia","bucharest","belgrade","athens","istanbul");
            add("athens","Grèce","Grèce",500,456,3,0,0,1,false,"Athènes","sofia","istanbul");
            add("istanbul","Turquie européenne","Turquie",620,420,4,1,0,2,true,"Istanbul","sofia","athens");
        }

        void loadSave() {
            // The map changed from a hand-drawn prototype to current real borders.
            // Reset incompatible old saves once so old IDs/owners cannot corrupt it.
            int saveVersion = 2;
            if (prefs.getInt("save_version", 0) != saveVersion) {
                prefs.edit().clear().putInt("save_version", saveVersion).apply();
                save();
                return;
            }
            if (!prefs.getBoolean("started", false)) { save(); return; }
            month=prefs.getInt("month",8); year=prefs.getInt("year",1939); turn=prefs.getInt("turn",1);
            supplies=prefs.getInt("supplies",130); manpower=prefs.getInt("manpower",90);
            gameOver=prefs.getBoolean("game_over",false);
            endMessage=prefs.getString("end_message","");
            for (Region r:regions) {
                String raw=prefs.getString("region_"+r.id,null);
                if(raw==null) continue;
                String[] a=raw.split("\\|",-1);
                try {
                    if (a.length < 6) continue;
                    r.owner=a[0]; r.infantry=Integer.parseInt(a[1]); r.tanks=Integer.parseInt(a[2]);
                    r.rockets=Integer.parseInt(a[3]); r.fort=Integer.parseInt(a[4]); r.general=Boolean.parseBoolean(a[5]);
                } catch(Exception ignored) {}
            }
        }

        void save() {
            SharedPreferences.Editor e=prefs.edit().putBoolean("started",true)
                .putBoolean("game_over",gameOver).putString("end_message",endMessage)
                .putInt("save_version",2)
                .putInt("month",month).putInt("year",year).putInt("turn",turn)
                .putInt("supplies",supplies).putInt("manpower",manpower);
            for(Region r:regions) e.putString("region_"+r.id,r.owner+"|"+r.infantry+"|"+r.tanks+"|"+r.rockets+"|"+r.fort+"|"+r.general);
            e.apply();
        }

        int baseColor(String owner) {
            switch(owner) {
                case "France": return 0xFF346BC0;
                case "Allemagne": return 0xFF777F89;
                case "Royaume-Uni": return 0xFF4F927C;
                case "URSS": return 0xFFB74745;
                case "Espagne": return 0xFFD2A84F;
                case "Portugal": return 0xFF5BA18E;
                case "Italie": return 0xFF58976A;
                case "Pologne": return 0xFFCB7657;
                case "Norvège": return 0xFF6AA7CB;
                case "Suède": return 0xFF6C98B7;
                case "Finlande": return 0xFF9DBACB;
                case "Danemark": return 0xFFBA6C73;
                case "Pays-Bas": return 0xFFE5A34F;
                case "Belgique": return 0xFFB9A05C;
                case "Luxembourg": return 0xFFCEB66B;
                case "Suisse": return 0xFFB65B55;
                case "Autriche": return 0xFFBA866D;
                case "Tchécoslovaquie": return 0xFF8E8DCC;
                case "Tchéquie": return 0xFF8E8DCC;
                case "Hongrie": return 0xFF8C9C55;
                case "Roumanie": return 0xFF6F9F52;
                case "Yougoslavie": return 0xFFAD8CBA;
                case "Bulgarie": return 0xFF9D7951;
                case "Grèce": return 0xFF5FABB6;
                case "Turquie": return 0xFFB96E5C;
                case "Lituanie": return 0xFF9BA2C3;
                case "Lettonie": return 0xFF8FABC0;
                case "Estonie": return 0xFF7CA9A3;
                case "Irlande": return 0xFF699A55;
                case "Albanie": return 0xFFB45A56;
                case "Andorre": return 0xFF9A9D7A;
                case "Bosnie-Herzégovine": return 0xFF8D87AE;
                case "Croatie": return 0xFF719D97;
                case "Islande": return 0xFF5D8EA8;
                case "Kosovo": return 0xFFB8A15C;
                case "Liechtenstein": return 0xFF8C9160;
                case "Macédoine du Nord": return 0xFFA77C91;
                case "Moldavie": return 0xFF7E9D60;
                case "Monaco": return 0xFFBA8270;
                case "Monténégro": return 0xFF7A9AB1;
                case "Russie": return 0xFFB74745;
                case "Saint-Marin": return 0xFF8A9C6A;
                case "Serbie": return 0xFFB98A6C;
                case "Slovaquie": return 0xFF6E9A92;
                case "Slovénie": return 0xFF8A86B1;
                case "Ukraine": return 0xFFB79D4D;
                case "Géorgie": return 0xFF709B7F;
                case "Arménie": return 0xFFAA7B60;
                case "Azerbaïdjan": return 0xFF6C93A8;
                case "Vatican": return 0xFFB4A6A6;
                default: return 0xFF999A9A;
            }
        }

        int shade(int color,int delta) {
            int r=Math.max(0,Math.min(255,Color.red(color)+delta));
            int g=Math.max(0,Math.min(255,Color.green(color)+delta));
            int b=Math.max(0,Math.min(255,Color.blue(color)+delta));
            return Color.rgb(r,g,b);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            scaleX=getWidth()/(float)W; scaleY=getHeight()/(float)H;
            canvas.save(); canvas.scale(scaleX,scaleY);
            drawBackground(canvas);
            for(Region r:regions) drawRegion(canvas,r);
            drawFrontLines(canvas);
            drawHeader(canvas);
            drawPanel(canvas);
            if(gameOver) drawGameOver(canvas);
            canvas.restore();
        }

        void text(Canvas c,String s,float x,float y,float size,int color,boolean bold) {
            p.setColor(color); p.setTextSize(size); p.setTypeface(bold?android.graphics.Typeface.create("sans-serif",1):android.graphics.Typeface.create("sans-serif",0));
            p.setStyle(Paint.Style.FILL); p.setStrokeWidth(1); c.drawText(s,x,y,p);
        }
        void rounded(Canvas c,float l,float t,float r,float b,float rad,int color) {
            p.setColor(color); p.setStyle(Paint.Style.FILL); c.drawRoundRect(new RectF(l,t,r,b),rad,rad,p);
        }
        void drawBackground(Canvas c) {
            c.drawColor(0xFF172C3C);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xFF213D4F); c.drawRect(0,70,MAP_RIGHT,H,p);
            p.setColor(0x223F7793); p.setStrokeWidth(1);
            for(int y=100;y<700;y+=45) {
                Path path=new Path(); path.moveTo(0,y);
                for(int x=0;x<MAP_RIGHT;x+=20) path.lineTo(x,y+(float)Math.sin((x+y)*0.017)*5);
                c.drawPath(path,p);
            }
            p.setStyle(Paint.Style.FILL);
            text(c,"OCÉAN ATLANTIQUE",24,430,10,0xFF81A9B8,false);
            text(c,"MER DU NORD",390,210,9,0xFF81A9B8,false);
            text(c,"MER BALTIQUE",534,170,9,0xFF81A9B8,false);
            text(c,"MÉDITERRANÉE",370,670,10,0xFF81A9B8,false);
        }

        void drawRegion(Canvas c,Region r) {
            if (!r.mapPaths.isEmpty()) {
                int hash=Math.abs(r.id.hashCode());
                int col=shade(baseColor(r.owner), (hash % 7) - 3);
                p.setStyle(Paint.Style.FILL);
                p.setColor(col);
                for (Path path : r.mapPaths) c.drawPath(path, p);

                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(0.85f);
                p.setColor(0xFFE1E3D8);
                for (Path path : r.mapPaths) c.drawPath(path, p);

                Region origin=byId.get(selectedId);
                if(origin!=null && "France".equals(origin.owner) &&
                        !"France".equals(r.owner) && adjacent(origin,r)) {
                    // Highlight every legal attack target so players can see where
                    // their selected region is allowed to attack.
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2.0f);
                    p.setColor(0xFFFFC45C);
                    for(Path path:r.mapPaths) c.drawPath(path,p);
                }

                if (r.id.equals(selectedId)) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3.0f);
                    p.setColor(0xFFFFE7A0);
                    for (Path path : r.mapPaths) c.drawPath(path, p);
                }
                if (r.id.equals(targetId)) {
                    p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3.0f);
                    p.setColor(0xFFFF885F);
                    for (Path path : r.mapPaths) c.drawPath(path, p);
                }
                p.setStyle(Paint.Style.FILL);
                if (!r.city.isEmpty() && (isMainCapital(r.city) || r.id.equals(selectedId) || r.id.equals(targetId))) {
                    p.setColor(0xFF17232A); c.drawCircle(r.cityX, r.cityY, 3.2f, p);
                    p.setColor(0xFFFFE7B3); c.drawCircle(r.cityX, r.cityY, 1.7f, p);
                    text(c, r.city, r.cityX + 4, r.cityY - 3, 8.5f, 0xFFFFF3D7, true);
                }
                if (r.id.equals(selectedId) || r.id.equals(targetId)) {
                    p.setColor(0xFFFFF3CF);
                    c.drawCircle(r.x, r.y, 2.2f, p);
                }
                return;
            }
            int hash=Math.abs(r.id.hashCode());
            float rx=28+(hash%5), ry=20+(hash%4);
            Path path=new Path();
            path.moveTo(r.x-rx*0.9f,r.y-ry*0.55f);
            path.lineTo(r.x-rx*0.45f,r.y-ry*1.0f);
            path.lineTo(r.x+rx*0.54f,r.y-ry*0.88f);
            path.lineTo(r.x+rx*1.0f,r.y-ry*0.1f);
            path.lineTo(r.x+rx*0.75f,r.y+ry*0.7f);
            path.lineTo(r.x+rx*0.05f,r.y+ry*1.0f);
            path.lineTo(r.x-rx*0.82f,r.y+ry*0.58f);
            path.close();
            int col=shade(baseColor(r.owner),((hash%13)-6));
            p.setStyle(Paint.Style.FILL); p.setColor(col); c.drawPath(path,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.6f); p.setColor(0xFFD8D5C3); c.drawPath(path,p);
            p.setStyle(Paint.Style.FILL);
            if(r.id.equals(selectedId)) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3.5f); p.setColor(0xFFFFE7A0); c.drawPath(path,p); p.setStyle(Paint.Style.FILL);
            }
            if(r.id.equals(targetId)) {
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(3.5f); p.setColor(0xFFFF885F); c.drawPath(path,p); p.setStyle(Paint.Style.FILL);
            }
            // Small city marker
            p.setColor(0xFFFFE7B8); c.drawCircle(r.x,r.y-4,2.7f,p);
            if(!r.city.isEmpty() && (r.id.equals(selectedId)||r.id.equals(targetId)||isMajor(r.id))) {
                text(c,r.city,r.x-14,r.y+29,8.5f,0xFFFFF3D7,true);
            }
            // Unit marker, with separate infantry/tank/rocket counts
            if(r.total()>0) {
                rounded(c,r.x-18,r.y+3,r.x+19,r.y+17,4,0xE91A2730);
                text(c,"I"+r.infantry, r.x-15,r.y+13,8,Color.WHITE,true);
                if(r.tanks>0) text(c,"T"+r.tanks,r.x+1,r.y+13,8,0xFFFFD18B,true);
                else if(r.rockets>0) text(c,"R"+r.rockets,r.x+1,r.y+13,8,0xFFFFA0A0,true);
            }
            if(r.fort>0) {
                p.setColor(0xFFEDD7A1); p.setStrokeWidth(1.2f); p.setStyle(Paint.Style.STROKE);
                c.drawRect(r.x-24,r.y-16,r.x-18,r.y-10,p); c.drawRect(r.x-22,r.y-18,r.x-20,r.y-16,p); p.setStyle(Paint.Style.FILL);
            }
            if(r.general) {
                p.setColor(0xFFFFD34F); c.drawCircle(r.x+22,r.y-14,5,p);
                text(c,"★",r.x+18.5f,r.y-11,8,0xFF573C16,true);
            }
        }

        boolean isMainCapital(String city) {
            return Arrays.asList("Paris","Londres","Berlin","Madrid","Rome","Varsovie","Moscou",
                "Lisbonne","Athènes","Vienne","Bruxelles","Amsterdam","Stockholm","Helsinki",
                "Kyiv","Bucarest","Dublin","Copenhague","Oslo","Prague","Belgrade","Ankara",
                "Reykjavik","Minsk","Sofia","Zagreb","Budapest","Riga","Vilnius","Tallinn").contains(city);
        }

        boolean isMajor(String id) {
            return Arrays.asList("paris","london","berlin","moscow","rome","madrid","warsaw","brussels",
                "amsterdam","stockholm","helsinki","athens","istanbul","kyiv","bucharest","belgrade","prague","lisbon").contains(id);
        }

        void drawFrontLines(Canvas c) {
            // Regional polygons now provide the real geographic boundaries.
            // No schematic centroid-to-centroid lines are drawn across the map.
        }

        void drawHeader(Canvas c) {
            rounded(c,0,0,W,64,0,0xFF101F2B);
            text(c,"THE WARGAME",24,29,22,0xFFF5F0E2,true);
            text(c,"EUROPE  •  FRONTIÈRES ADMINISTRATIVES RÉELLES",26,49,10,0xFF9FB2BC,true);
            rounded(c,415,12,637,52,8,0xFF263D4C);
            text(c,"TOUR "+turn,432,35,13,0xFFFFD58D,true);
            rounded(c,650,12,842,52,8,0xFF263D4C);
            text(c,months[month]+" "+year,665,35,12,0xFFF5F0E2,true);
        }

        void drawPanel(Canvas c) {
            rounded(c,870,0,1200,H,0,0xFF101F2B);
            text(c,"COMMANDEMENT",892,30,17,0xFFF4ECDD,true);
            text(c,"FRANCE  •  JOUEUR",893,51,10,0xFF80B6FA,true);
            rounded(c,890,66,1180,107,8,0xFF203747);
            text(c,"RAVITAILLEMENT",905,84,9,0xFFA7B8C0,true);
            text(c,""+supplies,905,100,18,0xFFFFD58D,true);
            text(c,"EFFECTIFS",1030,84,9,0xFFA7B8C0,true);
            text(c,""+manpower,1030,100,18,0xFFFFD58D,true);

            Region s=byId.get(selectedId), t=targetId==null?null:byId.get(targetId);
            text(c,"RÉGION SÉLECTIONNÉE",895,132,9,0xFF9DB2BD,true);
            if(s!=null) {
                text(c,clip(s.name,24),895,155,16,Color.WHITE,true);
                text(c,"Contrôle : "+s.owner,895,174,11,baseColor(s.owner),true);
                text(c,"Infanterie  "+s.infantry+"    Blindés  "+s.tanks,895,196,11,0xFFE4E7E7,false);
                text(c,"Roquettes   "+s.rockets+"    Forts   "+s.fort,895,214,11,0xFFE4E7E7,false);
                text(c,"Général : "+(s.general?"affecté":"aucun"),895,232,11,s.general?0xFFFFD34F:0xFFABB9C0,false);
            }
            rounded(c,890,244,1180,318,7,0xFF1B303D);
            if(t!=null) {
                text(c,"CIBLE D'ATTAQUE",903,261,9,0xFFFF9877,true);
                text(c,clip(t.name,28),903,281,13,Color.WHITE,true);
                text(c,clip(t.owner+"  •  "+t.total()+" unités • force "+t.strength(),39),903,299,9.5f,0xFFE0D9CF,false);
            } else {
                text(c,"COMMENT CONQUÉRIR ?",903,261,9,0xFF9DB2BD,true);
                text(c,"1. Touchez une région française.",903,280,10.5f,0xFFE4E7E7,true);
                text(c,"2. Touchez un voisin d'une autre couleur.",903,297,9.5f,0xFFE4E7E7,true);
                text(c,"3. Appuyez sur ATTAQUER.",903,312,9.5f,0xFFFFD58D,true);
            }

            boolean validTarget = targetId!=null && s!=null && t!=null &&
                "France".equals(s.owner) && !"France".equals(t.owner) &&
                adjacent(s,t) && s.total()>0 && !gameOver;
            button(c,890,326,1180,365,t==null?"⚔  ATTAQUER":"⚔  ATTAQUER : "+clip(t.name,12),
                0xFF974B42,validTarget);
            button(c,890,373,1033,416,"+ INFANTERIE",0xFF2C5C80,
                s!=null && "France".equals(s.owner) && supplies>=16 && manpower>=8 && !gameOver);
            button(c,1040,373,1180,416,"+ BLINDÉS",0xFF725A35,
                s!=null && "France".equals(s.owner) && supplies>=30 && manpower>=12 && !gameOver);
            button(c,890,424,1033,467,"+ ROQUETTES",0xFF604878,
                s!=null && "France".equals(s.owner) && supplies>=24 && manpower>=10 && !gameOver);
            button(c,1040,424,1180,467,"FORTIFIER",0xFF426C59,
                s!=null && "France".equals(s.owner) && supplies>=28 && s.fort<5 && !gameOver);
            button(c,890,475,1180,516,"AFFECTER UN GÉNÉRAL",0xFF77642F,
                s!=null && "France".equals(s.owner) && !s.general && supplies>=35 && !gameOver);
            rounded(c,890,526,1180,586,7,0xFF1C303C);
            text(c,"DERNIERS ÉVÉNEMENTS",902,544,9,0xFF9DB2BD,true);
            text(c,clip(log1,38),902,559,10,0xFFE7E6DF,false);
            text(c,clip(log2,38),902,572,10,0xFFE7E6DF,false);
            text(c,clip(log3,38),902,583,10,0xFFE7E6DF,false);
            button(c,890,601,1180,652,"TERMINER LE MOIS  ▶",0xFF9C7139,!gameOver);
            text(c,"Sélection → voisin ennemi → ATTAQUER",892,674,9,0xFF9FB2BC,true);
            text(c,"Les autres pays jouent à chaque mois.",892,688,9,0xFF9FB2BC,false);
            drawLegend(c);
        }

        void drawLegend(Canvas c) {
            int y=708;
            String[] names={"France","Allemagne","Espagne","Italie","Royaume-Uni","Russie"};
            int[] xs={20,119,225,323,414,548};
            for(int i=0;i<names.length;i++) {
                p.setColor(baseColor(names[i])); c.drawCircle(xs[i],y-4,4.5f,p);
                text(c,names[i],xs[i]+8,y,8.5f,0xFFD0D8D9,false);
            }
        }

        String clip(String s,int max) { return s.length()>max?s.substring(0,max-1)+"…":s; }

        void button(Canvas c,float l,float t,float r,float b,String label,int color,boolean enabled) {
            rounded(c,l,t,r,b,7,enabled?color:0xFF293943);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(enabled?0x66FFFFFF:0xFF394A53);
            c.drawRoundRect(new RectF(l,t,r,b),7,7,p); p.setStyle(Paint.Style.FILL);
            p.setTextSize(label.length()>18?10.5f:12); p.setTypeface(android.graphics.Typeface.create("sans-serif",1));
            p.setColor(enabled?Color.WHITE:0xFF7F8D93);
            float tw=p.measureText(label); c.drawText(label,l+(r-l-tw)/2,(t+b)/2+4,p);
        }

        void drawGameOver(Canvas c) {
            rounded(c,140,210,730,420,14,0xF0101D26);
            text(c,endMessage,190,285,27,0xFFFFE5A1,true);
            text(c,"Vous pouvez continuer la campagne ou relancer une nouvelle partie.",190,323,15,Color.WHITE,false);
        }

        Region hit(float x,float y) {
            // On a phone, tiny administrative regions can be smaller than a fingertip.
            // Prefer a region's real interior point when it is close to the tap.
            Region nearest=null;
            float nearestDistance=Float.MAX_VALUE;
            for(Region r:regions) {
                float dx=x-r.x, dy=y-r.y;
                float d=dx*dx+dy*dy;
                if(d<nearestDistance) {nearestDistance=d;nearest=r;}
            }
            if(nearest!=null && nearestDistance<=22f*22f) return nearest;

            for (int i=regions.size()-1; i>=0; i--) {
                Region r=regions.get(i);
                for (ArrayList<float[]> ring : r.mapRings) {
                    if (pointInPolygon(x,y,ring)) return r;
                }
            }
            return nearestDistance<=34f*34f ? nearest : null;
        }

        boolean pointInPolygon(float x, float y, ArrayList<float[]> points) {
            boolean inside=false;
            for(int i=0,j=points.size()-1;i<points.size();j=i++) {
                float xi=points.get(i)[0], yi=points.get(i)[1];
                float xj=points.get(j)[0], yj=points.get(j)[1];
                boolean crosses=((yi>y)!=(yj>y)) &&
                    (x < (xj-xi)*(y-yi)/(yj-yi+0.000001f)+xi);
                if(crosses) inside=!inside;
            }
            return inside;
        }

        boolean adjacent(Region a,Region b) {
            if(a==null||b==null)return false;
            for(String id:a.links) if(id.equals(b.id)) return true;
            return false;
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()!=MotionEvent.ACTION_UP) return true;
            float x=e.getX()/(getWidth()/(float)W), y=e.getY()/(getHeight()/(float)H);
            if(x>=870) {
                if(y>=326&&y<=365) attack();
                else if(y>=373&&y<=416) { if(x<1035) recruit("infantry"); else recruit("tanks"); }
                else if(y>=424&&y<=467) {
                    if(x<1035) recruit("rockets"); else fortify();
                }
                else if(y>=475&&y<=516) assignGeneral();
                else if(y>=601&&y<=652) nextMonth();
                invalidate(); return true;
            }
            Region r=hit(x,y);
            if(r!=null) {
                Region s=byId.get(selectedId);
                if("France".equals(r.owner)) {
                    selectedId=r.id;
                    targetId=null;
                    setLog("Région française sélectionnée : "+r.name,
                        "Choisissez une région ennemie voisine sur la carte.",
                        "Puis touchez ATTAQUER pour tenter de la conquérir.");
                } else if(s!=null && "France".equals(s.owner) && adjacent(s,r)) {
                    targetId=r.id;
                    setLog("Cible choisie : "+r.name,
                        "Contrôle actuel : "+r.owner+".",
                        "Touchez ATTAQUER pour lancer la conquête.");
                } else {
                    targetId=null;
                    setLog("Cible invalide : choisissez un voisin ennemi.",
                        "La région visée doit partager une frontière avec "+(s==null?"votre région":s.name)+".",
                        "Les provinces surlignées indiquent votre sélection.");
                }
                invalidate(); return true;
            }
            return true;
        }

        void setLog(String a,String b,String c) { log1=a; log2=b; log3=c; }
        void spend(int amount) { supplies-=amount; }
        void recruit(String type) {
            Region s=byId.get(selectedId);
            if(gameOver || s==null || !"France".equals(s.owner)) {setLog("Sélectionnez une région française.",log2,log3);return;}
            if("infantry".equals(type)) {
                if(supplies<16||manpower<8){setLog("Ressources insuffisantes.",log2,log3);return;}
                spend(16);manpower-=8;s.infantry+=2;setLog("Deux unités d'infanterie recrutées.",s.name,log3);
            } else if("tanks".equals(type)) {
                if(supplies<30||manpower<12){setLog("Pas assez de matériel pour les blindés.",log2,log3);return;}
                spend(30);manpower-=12;s.tanks++;setLog("Un bataillon blindé rejoint "+s.name+".",log2,log3);
            } else {
                if(supplies<24||manpower<10){setLog("Ressources insuffisantes pour l'artillerie.",log2,log3);return;}
                spend(24);manpower-=10;s.rockets++;setLog("Une unité d'artillerie est déployée.",s.name,log3);
            }
            save();invalidate();
        }
        void fortify() {
            Region s=byId.get(selectedId);
            if(gameOver||s==null||!"France".equals(s.owner))return;
            if(supplies<28){setLog("Il faut 28 points de ravitaillement.",log2,log3);return;}
            spend(28);s.fort=Math.min(5,s.fort+1);
            setLog("Fortifications renforcées : "+s.name+".","Bonus défensif accru.",log3);save();invalidate();
        }
        void assignGeneral() {
            Region s=byId.get(selectedId);
            if(gameOver||s==null||!"France".equals(s.owner))return;
            if(s.general){setLog("Un général commande déjà cette région.",log2,log3);return;}
            if(supplies<35){setLog("Il faut 35 points pour affecter un général.",log2,log3);return;}
            spend(35);s.general=true;setLog("Un général est affecté à "+s.name+".","Le commandement augmente la force de combat.",log3);save();invalidate();
        }

        void attack() {
            Region s=byId.get(selectedId), t=targetId==null?null:byId.get(targetId);
            if(gameOver || s==null || t==null || !"France".equals(s.owner) ||
                    "France".equals(t.owner) || !adjacent(s,t)) {
                setLog("Sélectionnez une région française puis une cible ennemie voisine.",
                    "Étape 1 : touchez une région bleue.", "Étape 2 : touchez une région ennemie voisine.");
                invalidate();return;
            }
            if(s.total()<1) {
                setLog("Cette région n'a plus d'unités prêtes à combattre.",
                    "Recrutez des troupes ou sélectionnez une autre région française.",log3);
                invalidate();return;
            }
            String captured=t.name;
            boolean won=battle(s,t,true);
            if(won) {
                t.owner="France";
                transferOccupationForce(s,t);
                t.fort=Math.max(0,t.fort-1);
                t.general=false;
                setLog("VICTOIRE : "+captured+" est conquise !",
                    "La région passe sous contrôle français.", "Appuyez sur Terminer le mois pour continuer.");
                if(countOwned("France") >= Math.ceil(regions.size() * 0.5)) {
                    gameOver=true;endMessage="VICTOIRE : DOMINATION DE L'EUROPE !";
                }
            } else {
                setLog("Attaque repoussée à "+captured+".",
                    "Des unités ont été perdues pendant le combat.", "Essayez de renforcer cette région avant une nouvelle attaque.");
            }
            targetId=null;save();invalidate();
        }

        void transferOccupationForce(Region from, Region to) {
            int moveInf=from.infantry/2;
            int moveTanks=from.tanks/2;
            int moveRockets=from.rockets/2;
            // If only one unit survived, move that actual unit instead of creating
            // a fictional extra infantry unit in the captured region.
            if(moveInf+moveTanks+moveRockets==0) {
                if(from.infantry>0) moveInf=1;
                else if(from.tanks>0) moveTanks=1;
                else if(from.rockets>0) moveRockets=1;
            }
            from.infantry-=moveInf;
            from.tanks-=moveTanks;
            from.rockets-=moveRockets;
            to.infantry=moveInf;
            to.tanks=moveTanks;
            to.rockets=moveRockets;
        }

        boolean battle(Region attacker,Region defender,boolean playerAttack) {
            double ap=(attacker.infantry+attacker.tanks*2.25+attacker.rockets*1.75+(attacker.general?3:0))*(0.88+random.nextDouble()*0.28);
            double dp=(defender.infantry+defender.tanks*2.2+defender.rockets*1.7+defender.fort*2.0+(defender.general?3:0))*(0.9+random.nextDouble()*0.25);
            boolean attackerWins=ap>dp;
            int desiredAttackLosses=Math.max(1,(int)Math.round(dp/Math.max(1,ap)*1.8));
            // A victorious attack must leave at least one real surviving unit.
            int maxAttackLosses=Math.max(0,attacker.total()-(attackerWins?1:0));
            int attackCas=Math.min(maxAttackLosses,desiredAttackLosses);
            int defendCas=defender.total()==0 ? 0 :
                Math.min(defender.total(),Math.max(1,(int)Math.round(ap/Math.max(1,dp)*1.8)));
            removeUnits(attacker,attackCas);
            removeUnits(defender,defendCas);
            return attackerWins && attacker.total()>0;
        }

        void removeUnits(Region r,int n) {
            while(n>0 && r.total()>0) {
                if(r.infantry>0)r.infantry--;
                else if(r.tanks>0)r.tanks--;
                else if(r.rockets>0)r.rockets--;
                n--;
            }
        }

        int countOwned(String owner) {
            int n=0;for(Region r:regions)if(owner.equals(r.owner))n++;return n;
        }

        void nextMonth() {
            if(gameOver)return;
            month++;
            if(month>=12){month=0;year++;}
            turn++; supplies+=22;manpower+=14;
            String battleNote="Les armées se réorganisent.";
            // Every non-player country is a bot. Each bot evaluates its borders at every turn.
            List<Region> order=new ArrayList<>(regions);Collections.shuffle(order,random);
            int botBattles=0;
            int botAttempts=0;
            int maxBotAttempts=Math.min(60,Math.max(16,regions.size()/30));
            for(Region a:order) {
                if(botAttempts>=maxBotAttempts) break;
                if("France".equals(a.owner)||a.total()<1)continue;
                List<Region> options=new ArrayList<>();
                for(String id:a.links) {
                    Region b=byId.get(id);
                    // Empty garrisons can still be conquered.
                    if(b!=null&&!b.owner.equals(a.owner)) options.add(b);
                }
                if(options.isEmpty()||random.nextDouble()>0.24)continue;
                botAttempts++;
                Region target=options.get(random.nextInt(options.size()));
                String oldOwner=target.owner;
                if(battle(a,target,false)) {
                    target.owner=a.owner;
                    transferOccupationForce(a,target);
                    target.fort=Math.max(0,target.fort-1);target.general=false;
                    if("France".equals(oldOwner)) battleNote="ALERTE : "+target.name+" est attaquée !";
                    else battleNote="Le front change près de "+target.name+".";
                    botBattles++;
                }
            }
            if(botBattles==0)battleNote="Les états-majors préparent leurs prochaines opérations.";
            setLog(months[month]+" "+year+" : ravitaillement reçu.",battleNote,"Les pays adverses sont contrôlés par les bots.");
            if(countOwned("France")==0) {gameOver=true;endMessage="LA FRANCE A ÉTÉ CONQUISE";}
            else if(countOwned("France") >= Math.ceil(regions.size() * 0.5)) {
                gameOver=true;endMessage="VICTOIRE : DOMINATION DE L'EUROPE !";
            }
            save();invalidate();
        }
    }
}