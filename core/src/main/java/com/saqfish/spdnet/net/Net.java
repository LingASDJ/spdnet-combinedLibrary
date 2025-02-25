/*
 * Pixel Dungeon
 * Copyright (C) 2021 saqfish
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package com.saqfish.spdnet.net;

import static java.util.Collections.singletonMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.saqfish.spdnet.Dungeon;
import com.saqfish.spdnet.ShatteredPixelDungeon;
import com.saqfish.spdnet.messages.Messages;
import com.saqfish.spdnet.net.events.Events;
import com.saqfish.spdnet.net.events.Send;
import com.saqfish.spdnet.net.windows.NetWindow;
import com.saqfish.spdnet.net.windows.WndNetOptions;
import com.saqfish.spdnet.net.windows.WndServerInfo;
import com.saqfish.spdnet.scenes.GameScene;
import com.saqfish.spdnet.ui.Icons;
import com.watabou.noosa.Game;
import com.watabou.utils.Callback;
import com.watabou.utils.DeviceCompat;

import org.json.JSONObject;

import java.net.URI;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class Net {
    public static String DEFAULT_HOST = "103.205.253.39";
    public static String DEFAULT_LH_HOST = "127.0.0.1";
    public static String DEFAULT_SCHEME = "http";
    public static String DEFAULT_KEY = "debug";
    public static long DEFAULT_ASSET_VERSION = 0;

    //子版本号
    public static final int NET_VERSION = 5;

    //测试服
    public static int DEFAULT_PORT = 11459;
    public static int DEFAULT_LH_PORT = 5800;

    private Socket socket;
    private Receiver receiver;
    private Sender sender;
    private ObjectMapper mapper;
    private Loader loader;
    private long seed;

    private NetWindow w;

    public Net(String address, String key){
        URI url = URI.create(address);
        Settings.scheme(url.getScheme());
        Settings.address(url.getHost());
        Settings.port(url.getPort());
        Settings.auth_key(key);
        session();
    }

    public Net(String address){
        this(address, DEFAULT_KEY);
        session();
    }

    public Net(){
        session();
    }

    public void reset() {
        session();
    }

    public void session(){
        URI url = Settings.uri();
        String key = Settings.auth_key();
        DeviceCompat.log("URL", url.toString());
        IO.Options options = IO.Options.builder()
                .setAuth(singletonMap("token", key))
                .setQuery("version=" + Game.versionCode + NET_VERSION)
                .build();
        socket = IO.socket(url, options);
        mapper = new ObjectMapper();
        loader = new Loader();
        receiver = new Receiver(this, mapper);
        sender = new Sender(this, mapper);
        setupEvents();
    }

    public void setupEvents(){
        Emitter.Listener onConnected = args -> {
            if(w != null) {
                Game.runOnRenderThread( () -> w.destroy());
            }
            if(Game.scene() instanceof GameScene)
                ShatteredPixelDungeon.net().sender().sendAction(Send.INTERLEVEL, Dungeon.hero.heroClass.ordinal(), Dungeon.depth, Dungeon.hero.pos);
        };

        Emitter.Listener onDisconnected = args -> {
            //disconnect();
        };

        // TODO: Clean this up or handle errors better
        Emitter.Listener onConnectionError = args -> {
            try {
                JSONObject data = (JSONObject)args[0];
                String json = data.getString("message");
                Events.Error e = mapper().readValue(json, Events.Error.class);

                if(e.type == 1){
                    Game.runOnRenderThread(new Callback() {
                        @Override
                        public void call() {
                            NetWindow.runWindow(new WndNetOptions(Icons.get(Icons.CHANGES),
                                    Messages.get(Net.class,"oldupdate"),
                                    //动态公告
                                    e.motd,
                                    Messages.get(Net.class,"download")){
                                @Override
                                protected void onSelect(int index) {
                                    super.onSelect(index);
                                    if (index == 0){
                                        //动态更新链接
                                        ShatteredPixelDungeon.platform.openURI(e.link);
                                    }
                                }
                            });
                            //
                        }
                    });
                }else NetWindow.error(e.data);
            }catch(Exception ignored) {
                ignored.printStackTrace();
                NetWindow.error("暂时无法连接到服务器！");
            }
            receiver.cancelAll();
            disconnect();
        };

        socket.on(Socket.EVENT_CONNECT_ERROR, onConnectionError);
        socket.on(Socket.EVENT_CONNECT, onConnected);
        socket.on(Socket.EVENT_DISCONNECT, onDisconnected);
    }

    public void endEvents(){
        socket.off();
    }

    public void connect() {
        receiver.startAll();
        socket.connect();
    }
    public void disconnect(){
        receiver.cancelAll();
        socket.disconnect();
    }

    public void toggle(WndServerInfo w) {
        this.w = w;
        if(!socket.connected() && !socket.io().isReconnecting())
            connect();
        else
            disconnect();
    }

    public void die(){
        if (socket != null) {
            if(socket.connected()) disconnect();
            endEvents();
            socket = null;
        }
        receiver = null;
        sender = null;
    }


    public void seed(long seed) { this.seed = seed; }
    public long seed() { return this.seed; }

    public Boolean connected() { return socket != null && socket.connected(); }
    public Socket socket(){ return this.socket; }
    public ObjectMapper mapper() { return this.mapper;}
    public Sender sender() { return sender; }
    public Receiver reciever() { return receiver; }
    public Loader loader() { return loader; }
    public URI uri(){ return Settings.uri(); }
}
