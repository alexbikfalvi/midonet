ip netns add left
ip link add name leftdp type veth peer name leftns
ip link set leftdp up
ip link set leftns netns left
ip netns exec left ip link set leftns up
ip netns exec left ip address add 10.25.25.1/24 dev leftns
ip netns exec left ifconfig lo up
