package eu.javaexperience.rpc.web;

import eu.javaexperience.rpc.SimpleRpcRequest;
import eu.javaexperience.rpc.SimpleRpcSession;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import eu.javaexperience.rpc.http.RpcHttpTools;

public abstract class UsualRpcUrlNode extends RpcUrlNode<SimpleRpcRequest, SimpleRpcSession>
{
	public UsualRpcUrlNode
	(
		String nodeName,
		String sessionKey
	)
	{
		super
		(
			nodeName,
			BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER,
			RpcHttpTools.generateGetOrCreateSessionGetter(sessionKey, BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER),
			RpcHttpTools.WRAP_SIMPLE_RPC_REQUEST
		);
	}
}
