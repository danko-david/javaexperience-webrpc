package eu.javaexperience.rpc.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;

import eu.javaexperience.collection.map.NullMap;
import eu.javaexperience.collection.map.OneShotMap;
import eu.javaexperience.datareprez.DataCommon;
import eu.javaexperience.datareprez.DataObject;
import eu.javaexperience.datareprez.DataSender;
import eu.javaexperience.dispatch.Dispatcher;
import eu.javaexperience.interfaces.simple.getBy.GetBy1;
import eu.javaexperience.interfaces.simple.getBy.GetBy2;
import eu.javaexperience.interfaces.simple.publish.SimplePublish2;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.rpc.RpcFunction;
import eu.javaexperience.rpc.RpcProtocolHandler;
import eu.javaexperience.rpc.RpcRequest;
import eu.javaexperience.rpc.RpcSession;
import eu.javaexperience.rpc.RpcSessionTools;
import eu.javaexperience.rpc.RpcTools;
import eu.javaexperience.rpc.SimpleRpcRequest;
import eu.javaexperience.rpc.SimpleRpcSession;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcProtocolHandler;
import eu.javaexperience.rpc.codegen.JavascriptRpcSourceGenerator;
import eu.javaexperience.rpc.function.RpcFunctionParameter;
import eu.javaexperience.web.Context;
import eu.javaexperience.web.MIME;
import eu.javaexperience.web.features.WebSocket;
import eu.javaexperience.web.features.WebSocketEndpoint;

public class RpcHttpTools
{
	public static final GetBy2<SimpleRpcRequest, DataObject, SimpleRpcSession> WRAP_SIMPLE_RPC_REQUEST = new GetBy2<SimpleRpcRequest, DataObject, SimpleRpcSession>()
	{
		@Override
		public SimpleRpcRequest getBy(DataObject a, SimpleRpcSession b)
		{
			return new SimpleRpcRequest(b, a);
		}
	};
	
	public static DataObject examineRequest(Context ctx, RpcProtocolHandler protocol)
	{
		byte[] data = (byte[]) ctx.getRequest().getAttribute("data");
		
		if(null == data)
		{
			return null;
		}
		
		return protocol.getDefaultCommunicationProtocolPrototype().objectFromBlob(data);
	}
	
	public static <R extends RpcRequest, S extends RpcSession> DataObject dispatchSingleRequest
	(
		S session,
		GetBy2<R, DataObject, S> wrapRequest,
		DataObject request,
		GetBy1<DataObject, R> dispatch
	)
	{
		R req = wrapRequest.getBy(request, session);//new SimpleRpcRequest(session, request);
		return dispatch.getBy(req);
	}
	
	public static <R extends RpcRequest, S extends RpcSession> DataObject dispatchSingleRequest
	(
		DataObject request,
		Context ctx,
		GetBy2<R, DataObject, S>  wrapRequest,
		GetBy1<S, Context> getSession,
		RpcProtocolHandler protocol,
		GetBy1<DataObject, R> dispatch
	)
	{
		S session = getSession.getBy(ctx);
		RpcSessionTools.setCurrentRpcSession(session);
		try
		{
			return dispatchSingleRequest(session, wrapRequest, request, dispatch);
		}
		finally
		{
			RpcSessionTools.setCurrentRpcSession(null);
		}
	}
	
	public static <R extends RpcRequest, S extends RpcSession> void serveRpcAjaxRequest
	(
		Context ctx,
		GetBy2<R, DataObject, S> wrapRequest,
		GetBy1<S, Context> getSession,
		RpcProtocolHandler protocol,
		GetBy1<DataObject, R> dispatch
	)
		throws IOException
	{
		DataObject reqDo = examineRequest(ctx, protocol);
		if(null != reqDo)
		{
			serveRequest(ctx, wrapRequest, getSession, protocol, dispatch, reqDo);
		}
	}
	
	public static <R extends RpcRequest, S extends RpcSession> void serveRequest
	(
		Context ctx,
		GetBy2<R, DataObject, S> wrapRequest,
		GetBy1<S, Context> getSession,
		RpcProtocolHandler protocol,
		GetBy1<DataObject, R> dispatch,
		DataObject request
	)
		throws IOException
	{
		if(null != request)
		{
			response(ctx, dispatchSingleRequest(request, ctx, wrapRequest, getSession, protocol, dispatch), protocol);
		}
	}

	public static GetBy1<SimpleRpcSession, Context> generateGetOrCreateSessionGetter
	(
		final String sessionKey,
		final RpcProtocolHandler protocol
	)
	{
		return new GetBy1<SimpleRpcSession, Context>()
		{
			@Override
			public SimpleRpcSession getBy(Context a)
			{
				Map<String, Object> env = a.getSession().getEnv();
				SimpleRpcSession sess = (SimpleRpcSession) env.get(sessionKey);
				if(null == sess)
				{
					env.put(sessionKey, sess = new SimpleRpcSession(protocol));
				}
				
				return sess;
			}
		};
	}
	
	public static Dispatcher<Context> serveGeneratedJsApi(Collection<? extends RpcFunction> collection, String name, boolean async)
	{
		final byte[] RPC_SORUCE = JavascriptRpcSourceGenerator.BASIC_JAVASCRIPT_SOURCE_BUILDER.buildRpcClientSource
		(
			name,
			(Collection<RpcFunction<RpcRequest, RpcFunctionParameter>>) (Object) collection,
			async?new OneShotMap<>("using_callback_return", "true"):NullMap.instance
		).getBytes();
		
		return new Dispatcher<Context>()
		{
			@Override
			public boolean dispatch(Context ctx)
			{
				try
				{
					ctx.getResponse().setContentType("text/javascript");
					ServletOutputStream os = ctx.getResponse().getOutputStream();
					os.write(RPC_SORUCE);
					os.flush();
				}
				catch(Exception e)
				{
					Mirror.propagateAnyway(e);
				}
				
				ctx.finishOperation();
				return true;
			}
		};
	}

	public static <R extends RpcRequest, S extends RpcSession> Dispatcher<Context> generateAjaxRequestDisptcher
	(
		final RpcProtocolHandler protocol,
		final GetBy2<R, DataObject, S> wrapRequest,
		final GetBy1<S, Context> getSess,
		final GetBy1<DataObject, R> dispatch
	)
	{
		return new Dispatcher<Context>()
		{
			@Override
			public boolean dispatch(Context ctx)
			{
				try
				{
					RpcHttpTools.serveRpcAjaxRequest
					(
						ctx,
						wrapRequest,
						getSess,
						protocol,
						dispatch
					);
				}
				catch(Exception e)
				{
					Mirror.propagateAnyway(e);
				}
				return true;
			}
		};
	}
	
	public static void response(Context ctx, DataObject response, RpcProtocolHandler protocol) throws IOException
	{
		response(ctx, response, protocol.getDefaultCommunicationProtocolPrototype());
	}

	public static void response(Context ctx, DataObject response, DataCommon proto) throws IOException
	{
		HttpServletResponse resp = ctx.getResponse();
		resp.setContentType(MIME.json.mime);
		resp.setCharacterEncoding("UTF-8");
		ServletOutputStream os = resp.getOutputStream();
		DataSender sender = proto.newDataSender(os);
		sender.send(response);
		os.flush();
		ctx.finishOperation();
	}
	
	public static void handleWebsocketRequest(GetBy1<DataObject, SimpleRpcRequest> dispatcher, SimplePublish2<Boolean, WebSocket> connect_dc, Context ctx, SimpleRpcSession session) throws NoSuchAlgorithmException, IOException
	{
		WebSocket ws = WebSocketEndpoint.upgradeRequest(ctx);
		
		try
		{
			if(null != connect_dc)
			{
				connect_dc.publish(true, ws);
			}
			
			RpcProtocolHandler protocol = session.getDefaultRpcProtocolHandler();
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DataSender ds = protocol.getDefaultCommunicationProtocolPrototype().newDataSender(baos);
			
			while(true)
			{
				try
				{
					baos.reset();
					DataObject rpcReq = protocol.getDefaultCommunicationProtocolPrototype().objectFromBlob(ws.receive());
					ds.send(dispatcher.getBy(new SimpleRpcRequest(session, rpcReq)));
				}
				catch(IOException e)
				{
					ds.send(RpcTools.wrapException(new SimpleRpcRequest(session), e));
				}
				finally
				{
					ws.send(baos.toByteArray());
				}
			}
		}
		finally
		{
			if(null != connect_dc)
			{
				connect_dc.publish(false, ws);
			}
		}
	}
	
	public static <R extends RpcRequest, S extends RpcSession> void handleWebsocketRequest
	(
		Context ctx,
		GetBy2<R, DataObject, S> wrapRequest,
		GetBy1<S, Context> getSession,
		RpcProtocolHandler protocol,
		GetBy1<DataObject, R> dispatcher,
		SimplePublish2<Boolean, WebSocket> pub_ws_connect_dc
	)
		throws NoSuchAlgorithmException, IOException
	{
		WebSocket ws = WebSocketEndpoint.upgradeRequest(ctx);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataSender ds = protocol.getDefaultCommunicationProtocolPrototype().newDataSender(baos);
		
		S session = getSession.getBy(ctx);
		RpcSessionTools.setCurrentRpcSession(session);
		
		try
		{
			if(null != pub_ws_connect_dc)
			{
				pub_ws_connect_dc.publish(Boolean.TRUE, ws);
			}
			
			while(true)
			{
				try
				{
					baos.reset();
					DataObject rpcReq = protocol.getDefaultCommunicationProtocolPrototype().objectFromBlob(ws.receive());
					ds.send(dispatcher.getBy(wrapRequest.getBy(rpcReq, session)));
				}
				catch(IOException e)
				{
					ds.send(RpcTools.wrapException(new SimpleRpcRequest(session), e));
				}
				finally
				{
					ws.send(baos.toByteArray());
				}
			}
		}
		finally
		{
			RpcSessionTools.setCurrentRpcSession(null);
			if(null != pub_ws_connect_dc)
			{
				pub_ws_connect_dc.publish(Boolean.FALSE, ws);
			}
		}
	}
	
	public static void sendWebsocketServerEvent(RpcSession sess, WebSocket ws, String namespace, String _this, String method, Object... arguments) throws IOException
	{
		BidirectionalRpcProtocolHandler PROTO = (BidirectionalRpcProtocolHandler) sess.getDefaultRpcProtocolHandler();
		RpcRequest req = RpcTools.createClientNamespaceInvocation(sess, 0, namespace, _this, method, arguments);
		ws.send(req.getRequestData().toBlob());
	}
	
}
