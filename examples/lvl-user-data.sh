#!/bin/sh
create-fogbow-tunnel http 80
sed -i 's/include \/etc\/nginx\/naxsi//g' /etc/nginx/sites-enabled/default
service nginx restart
