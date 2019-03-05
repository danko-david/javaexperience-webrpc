package eu.javaexperience.rpc.web;

import java.io.IOException;
import java.lang.reflect.Method;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import eu.javaexperience.arrays.ArrayTools;
import eu.javaexperience.collection.CollectionTools;
import eu.javaexperience.collection.map.SmallMap;
import eu.javaexperience.datareprez.DataObject;
import eu.javaexperience.interfaces.simple.getBy.GetBy1;
import eu.javaexperience.interfaces.simple.getBy.GetBy2;
import eu.javaexperience.interfaces.simple.publish.SimplePublish2;
import eu.javaexperience.reflect.Mirror;
import eu.javaexperience.rpc.RpcFacility;
import eu.javaexperience.rpc.RpcProtocolHandler;
import eu.javaexperience.rpc.RpcRequest;
import eu.javaexperience.rpc.RpcSession;
import eu.javaexperience.rpc.RpcTools;
import eu.javaexperience.rpc.SimpleRpcRequest;
import eu.javaexperience.rpc.SimpleRpcSession;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcDefaultProtocol;
import eu.javaexperience.rpc.bidirectional.BidirectionalRpcProtocolHandler;
import eu.javaexperience.rpc.codegen.AstInterfaceGenerator;
import eu.javaexperience.rpc.codegen.JavaRpcInterfaceGenerator;
import eu.javaexperience.rpc.codegen.JavascriptRpcSourceGenerator;
import eu.javaexperience.rpc.codegen.PhpRpcInterfaceGenerator;
import eu.javaexperience.rpc.codegen.RpcSourceBuilder;
import eu.javaexperience.rpc.http.RpcHttpTools;
import eu.javaexperience.text.StringTools;
import eu.javaexperience.url.UrlTools;
import eu.javaexperience.web.Context;
import eu.javaexperience.web.HttpTools;
import eu.javaexperience.web.MIME;
import eu.javaexperience.web.dispatch.url.JavaClassURLNode;
import eu.javaexperience.web.facility.SiteFacilityTools;
import eu.javaexperience.web.features.WebSocket;

/**
 * TODO: api name extract, dispatch list available apis
 */
public abstract class RpcUrlNode<R extends RpcRequest, S extends RpcSession> extends JavaClassURLNode
{
	protected final BidirectionalRpcProtocolHandler<S> protocol;
	protected final GetBy1<S, Context> getSession;
	protected final GetBy2<R, DataObject, S> wrapRequest;
	
	public RpcUrlNode
	(
		String nodeName,
		BidirectionalRpcProtocolHandler<S> proto,
		GetBy1<S, Context> getSession,
		GetBy2<R, DataObject, S> wrapRequest
	)
	{
		super(nodeName);
		this.protocol = proto;
		this.getSession = getSession;
		this.wrapRequest = wrapRequest;
	}
	
	public static RpcUrlNode<SimpleRpcRequest, SimpleRpcSession> createSimple
	(
		String nodeName,
		final String usePostfix,
		final RpcFacility... apis
	)
	{
		final GetBy1<DataObject, SimpleRpcRequest> dispatcher = RpcTools.createSimpleNamespaceDispatcherWithDiscoverApi(apis);
		
		BidirectionalRpcProtocolHandler<SimpleRpcSession> PROTOCOL = BidirectionalRpcDefaultProtocol.DEFAULT_PROTOCOL_HANDLER_WITH_CLASS;
		RpcUrlNode<SimpleRpcRequest, SimpleRpcSession> ret = new RpcUrlNode<SimpleRpcRequest, SimpleRpcSession>
		(
			nodeName,
			PROTOCOL,
			RpcHttpTools.generateGetOrCreateSessionGetter("RPC_SESSION_OF_"+nodeName, PROTOCOL),
			RpcHttpTools.WRAP_SIMPLE_RPC_REQUEST
		)
		{

			@Override
			protected void fillApis
			(
				List<RpcFacility> dst,
				Context req,
				boolean all
			)
			{
				CollectionTools.copyInto(apis, dst);
			}

			@Override
			protected GetBy1<DataObject, SimpleRpcRequest> getDispatcher(Context ctx)
			{
				return dispatcher;
			}
			
			@Override
			protected String formatApiName(RpcFacility node)
			{
				if(null == usePostfix)
				{
					return node.getRpcName();
				}
				else
				{
					return node.getRpcName()+usePostfix;
				}
			}
			
			@Override
			protected String extractApiName(String name)
			{
				if(null == usePostfix)
				{
					return name;
				}
				else
				{
					return StringTools.getSubstringBeforeLastString(name, usePostfix);
				}
			}
		};
		
		return ret;
	}
	
	protected final Map<String, RpcSourceBuilder> targetLanguages = new HashMap<String, RpcSourceBuilder>()
	{
		{
			put("javascript", JavascriptRpcSourceGenerator.BASIC_JAVASCRIPT_SOURCE_BUILDER);
			put("php", PhpRpcInterfaceGenerator.BASIC_PHP_SOURCE_BUILDER);
			put("java", JavaRpcInterfaceGenerator.BASIC_JAVA_SOURCE_BUILDER);
			put("ast", AstInterfaceGenerator.BASIC_AST_SOURCE_BUILDER);
		}
	};
	
	protected RpcSourceBuilder getLang(Context ctx)
	{
		String lang = ctx.getRequest().getParameter("lang");
		if(null == lang)
		{
			SiteFacilityTools.finishWithElementSend(ctx, "Specify one language (GET parameter \"lang\")! Available languages: "+CollectionTools.toString(targetLanguages.keySet()));
		}
		
		RpcSourceBuilder ret = (RpcSourceBuilder) targetLanguages.get(lang);
		
		if(null == ret)
		{
			SiteFacilityTools.finishWithElementSend(ctx, "Select a correct one of the available languages (GET parameter \"lang\"): "+CollectionTools.toString(targetLanguages.keySet()));
		}
		
		return ret;
	}
	
	public String formatApis(Collection<? extends RpcFacility> apis, RpcSourceBuilder lang, Map<String, String> opts)
	{
		StringBuilder sb = new StringBuilder();
		
		for(RpcFacility api:apis)
		{
			sb.append(lang.buildRpcClientSource(formatApiName(api), api.getWrappedFunctions(), opts));
			sb.append("\n\n");
		}
		
		return sb.toString();
	}
	
	protected void source(Context ctx)
	{
		Map<String, String> opts = new SmallMap<>();
		
		UrlTools.fillMultiMap(opts, ctx.getRequest().getParameterMap());
		
		List<RpcFacility> apis = new ArrayList<>();
		
		fillApis(apis, ctx, false);
		
		if(0 == apis.size())
		{
			fillApis(apis, ctx, false);
			ArrayList<String> strs = new ArrayList<>();
			for(RpcFacility api:apis)
			{
				strs.add(formatApiName(api));
			}
			
			SiteFacilityTools.finishWithElementSend(ctx, "Specify one api (GET parameter \"api\")! Available apis: "+CollectionTools.toString(strs));
		}
		
		SiteFacilityTools.finishWithMimeSend(ctx, formatApis(apis, getLang(ctx), opts), MIME.javascript);

	}
	
	protected String[] getRequestedApis(Context ctx)
	{
		String[] req = ctx.getRequest().getParameterValues("api");
		if(null == req)
		{
			req = Mirror.emptyStringArray;
		}
		req = ArrayTools.withoutNulls(req);
		
		for(int i=0;i<req.length;++i)
		{
			req[i] = extractApiName(req[i]);
		}
		
		return req;
	}
	
	protected abstract void fillApis
	(
		List<RpcFacility> dst,
		Context req,
		boolean all
	);
	
	protected abstract GetBy1<DataObject, R> getDispatcher(Context ctx);
	
	protected String formatApiName(RpcFacility node)
	{
		return node.getRpcName();
	}
	
	protected String extractApiName(String name)
	{
		return name;
	}
	
	protected void ajax(Context ctx) throws IOException
	{
		RpcHttpTools.serveRpcAjaxRequest(ctx, wrapRequest, getSession, (RpcProtocolHandler) protocol, getDispatcher(ctx));
	}
	
	public DataObject serveRequest(Context ctx, DataObject req)
	{
		return RpcHttpTools.dispatchSingleRequest(req, ctx, wrapRequest, getSession, (RpcProtocolHandler) protocol, getDispatcher(ctx));
	}
	
	public void websocket(Context ctx) throws NoSuchAlgorithmException, IOException
	{
		RpcHttpTools.handleWebsocketRequest(ctx, wrapRequest, getSession, protocol, getDispatcher(ctx), websocketEvent);
	}
	
	protected void onWebsocketConnection(boolean true_connect_false_dc, WebSocket ws)
	{
		
	}
	
	protected final SimplePublish2<Boolean, WebSocket> websocketEvent = new SimplePublish2<Boolean, WebSocket>()
	{
		@Override
		public void publish(Boolean a, WebSocket b)
		{
			onWebsocketConnection(a, b);
		}
	};
	
	@Override
	public boolean dispatch(Context ctx)
	{
		String next = ctx.getRequestUrl().getCurrentURLElement();
		if(null != next)
		{
			try
			{
				switch (next)
				{
				case "source":
					source(ctx);
				case "ajax":
					ajax(ctx);
				case "websocket":
					websocket(ctx);
					break;
				}
			}
			catch(Exception e)
			{
				Mirror.propagateAnyway(e);
			}
		}
		
		return super.dispatch(ctx);
	}
	
	@Override
	protected boolean beforeCall(Context ctx, Method m)
	{
		return false;
	}

	@Override
	protected void afterCall(Context ctx, Method m)
	{
	}

	@Override
	protected void backward(Context ctx)
	{
	}

	@Override
	protected boolean endpoint(Context ctx)
	{
		return false;
	}

	@Override
	protected boolean access(Context ctx)
	{
		return true;
	}

	public List<RpcFacility> getApis()
	{
		ArrayList<RpcFacility> ret = new ArrayList<>();
		fillApis(ret, null, true);
		return ret;
	}
}
