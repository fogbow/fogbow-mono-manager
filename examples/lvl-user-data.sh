#!/bin/sh
LVL_REMOTE_PORT=$(curl -X POST #TOKEN_HOST#:#TOKEN_HOST_HTTP_PORT#/token/#TOKEN_ID#-lvl)
ssh -o UserKnownHostsFile=/dev/null -o StrictHostKeyChecking=no -o ServerAliveInterval=30 -N -f -R 0.0.0.0:$LVL_REMOTE_PORT:localhost:80 #TOKEN_ID#-lvl@#TOKEN_HOST# -p #TOKEN_HOST_SSH_PORT#
sudo sed -i ':a;N;$!ba;s/#\(location \/RequestDenied {\)\n\s\+#\([^\n]\+\)\n\s\+#/\1\n\2\n/g' /etc/nginx/sites-enabled/default
sudo sed -i s/lvl\.i3m\.upv\.es/#TOKEN_HOST#:$LVL_REMOTE_PORT/g /opt/lvl/htdocs/js/apps/config/marionette/configuration.js
sudo sed -i 's/waitSeconds : 7/waitSeconds : 30/g' /opt/lvl/htdocs/js/requirejs_main.js
sudo service nginx restart
