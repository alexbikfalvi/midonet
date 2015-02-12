ip netns add right
ip link add name rightdp type veth peer name rightns
ip link set rightdp up
ip link set rightns netns right
ip netns exec right ip link set rightns up
ip netns exec right ip address add 10.25.25.2/24 dev rightns
ip netns exec right ifconfig lo up
