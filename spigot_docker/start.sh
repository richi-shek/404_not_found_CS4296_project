#!/bin/bash
if [[ -n "$FRP_PUBLIC_IP" && -n "$FRP_SERVER_PORT" && -n "$PORT" && -n "$FRP_TOKEN"  ]]; then
 echo -e "serverAddr = \"$FRP_PUBLIC_IP\"\nserverPort = $FRP_SERVER_PORT\nauth.token = \"$FRP_TOKEN\"\ntransport.protocol = \"kcp\"\n\n[[proxies]]\nname = \"mc_port_$PORT\"\ntype = \"tcp\"\nlocalIP = \"127.0.0.1\"\nlocalPort = 25565\nremotePort = $PORT" > ./frpc.toml
 ./frpc -c ./frpc.toml &
fi
java -Xmx1536M -Xms512M -jar spigot-1.16.5.jar nogui
