package eu.javaexperience.web.spider;

import java.io.IOException;

import eu.javaexperience.datareprez.jsonImpl.DataObjectJsonImpl;
import eu.javaexperience.proxy.ProxyStorage;
import eu.javaexperience.proxy.TorProxySpawner.AbstractProxySource;
import eu.javaexperience.proxy.TorProxySpawner.ProxySource;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import eu.javaexperience.rpc.javaclient.JavaRpcClientTools;

public class RpcSpiderTools
{
	public static interface ProxySpawnerApi
	{
		public int get_proxy_offset(int a);
	} 
	
	public static ProxyStorage connectToProxySpawnerApi(String ip, int port, int maxCapacity) throws IOException
	{
		ProxySpawnerApi api = (ProxySpawnerApi) JavaRpcClientTools.createApiWithIpPort
		(
			ProxySpawnerApi.class,
			ip,
			port,
			"TorProxyServer",
			new BidirectionalRpcDefaultProtocol<>(new DataObjectJsonImpl())
		);
	
		return new ProxyStorage()
		{
			@Override
			public ProxySource getAtOffset(int i) throws IOException
			{
				synchronized (api)
				{
					return AbstractProxySource.createLocalSocksProxy(api.get_proxy_offset(i));
				}
			}

			@Override
			public int size()
			{
				return maxCapacity;
			}
		};
	}
}
