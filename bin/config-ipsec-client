config_setup="config setup\n charondebug="all"\n uniqueids=yes\n strictcrlpolicy=no\nconn %default\nconn tunnel"
left=$1
leftsourceip=$2
right=$3
rightsubnet=$4
tunnel_param_configs=" left="$left"\n leftsourceip"=$leftsourceip"\n right="$right"\n rightsubnet="$rightsubnet
other_tunnel_configs=" ike=aes256-sha2_256-modp1024!\n esp=aes256-sha2_256!\n keyingtries=0\n ikelifetime=1h\n lifetime=8h\n dpddelay=30\n dpdtimeout=120\n dpdaction=restart\n authby=secret\n auto=start\n keyexchange=ikev2\n type=tunnel\n closeaction=restart"

echo -e $config_setup'\n'$tunnel_param_configs'\n'$other_tunnel_configs > /etc/ipsec.conf
ipsec restart
