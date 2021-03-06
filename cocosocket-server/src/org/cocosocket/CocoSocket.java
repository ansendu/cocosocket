/**
 * nsocket，基于netty实现
 *//**
 * nsocket，基于netty实现
 */
package org.cocosocket;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.cocosocket.framing.Framedata;
import org.cocosocket.protocal.Protocal;

/**
 *
 * @author beykery
 */
public final class CocoSocket extends ChannelInboundHandlerAdapter
{

    private static final Logger LOG = Logger.getLogger(CocoSocket.class.getName());
    private SocketListener listener;
    private final SocketChannel channel;
    private boolean closeReason;//是否为服务器主动关闭
    private final Protocal protocal;
    private long sessionId;
    private Map<String, Object> sessions;
    private ChannelHandlerContext context;
    private String closeReasonString;//断开原因描述

    /**
     * 连接
     *
     * @param l
     * @param ch
     * @param protocal
     */
    public CocoSocket(SocketListener l, SocketChannel ch, Protocal protocal)
    {
        channel = ch;
        this.listener = l;
        this.protocal = protocal;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception
    {
        final ByteBuf bb = (ByteBuf) msg;
        try
        {
            ByteBuf fd = protocal.translateFrame(bb);
            while (fd != null)
            {
                deliverMessage(fd);
                fd = protocal.translateFrame(bb);
            }
        } finally
        {
            bb.release();
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception
    {
        this.listener.onError(this, cause);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception
    {
        this.context = ctx;
        this.listener.onOpen(this);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception
    {
        this.listener.socketClosed(this, closeReason);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception
    {
        if (evt instanceof IdleStateEvent)
        {
            IdleStateEvent event = (IdleStateEvent) evt;
            this.listener.onIdle(this, event);
        }
    }

    /**
     * 断开连接
     *
     * @return
     */
    public ChannelFuture close()
    {
        this.closeReason = true;
        return this.channel.close();
    }

    /**
     * 断开连接
     *
     * @param reason
     * @return
     */
    public ChannelFuture close(String reason)
    {
        this.closeReasonString = reason;
        this.closeReason = true;
        return this.channel.close();
    }

    /**
     * 发送一帧（完整的一帧）
     *
     * @param data
     * @return
     */
    public ChannelFuture sendFrame(ByteBuf data)
    {
        return this.context.writeAndFlush(data);
    }

    /**
     * 发送一帧
     *
     * @param data
     * @return
     */
    public ChannelFuture sendFrame(Framedata data)
    {
        return this.context.writeAndFlush(data.getData());
    }

    private void deliverMessage(ByteBuf fd)
    {
        this.listener.onMessage(this, fd);
    }

    public boolean isOpen()
    {
        return this.channel.isActive();
    }

    public long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(long sessionId)
    {
        this.sessionId = sessionId;
    }

    /**
     * 获取session值
     *
     * @param key
     * @return
     */
    public Object getSession(String key)
    {
        return sessions == null ? null : sessions.get(key);
    }

    /**
     * 获取session值
     *
     * @param key
     * @param def
     * @return
     */
    public Object getSession(String key, Object def)
    {
        Object r = sessions == null ? null : sessions.get(key);
        return r == null ? def : r;
    }

    /**
     * 删除
     *
     * @param key
     * @return
     */
    public Object removeSession(String key)
    {
        return sessions == null ? null : sessions.remove(key);
    }

    /**
     * 清空session数据
     */
    public void clearSession()
    {
        sessions = null;
    }

    /**
     * 设置session值
     *
     * @param key
     * @param value
     */
    public void setSession(String key, Object value)
    {
        if (sessions == null)
        {
            sessions = new HashMap<>();
        }
        sessions.put(key, value);
    }

    /**
     * 获取所有的session键
     *
     * @return
     */
    public Set<String> getSessionKeys()
    {
        return sessions == null ? null : sessions.keySet();
    }

    @Override
    public String toString()
    {
        InetSocketAddress local = channel.localAddress();
        InetSocketAddress remote = channel.remoteAddress();
        return local.getAddress().getHostAddress() + ":" + local.getPort() + "=>" + remote.getAddress().getHostAddress() + ":" + remote.getPort();
    }

    /**
     * 远程地址
     *
     * @return
     */
    public InetSocketAddress remoteAddress()
    {
        return channel.remoteAddress();
    }

    /**
     * 本地地址
     *
     * @return
     */
    public InetSocketAddress localAddress()
    {
        return channel.localAddress();
    }

    public void setListener(SocketListener listener)
    {
        this.listener = listener;
    }

    public SocketListener getListener()
    {
        return listener;
    }

    /**
     * 获取channel
     *
     * @return
     */
    public SocketChannel channel()
    {
        return channel;
    }

    /**
     * 断开原因
     *
     * @return
     */
    public String getCloseReason()
    {
        return closeReasonString;
    }

    /**
     * 设置idle
     *
     * @param readerIdleTime
     * @param writerIdleTime
     * @param allIdleTime
     * @param unit
     */
    public void idle(long readerIdleTime, long writerIdleTime, long allIdleTime, TimeUnit unit)
    {
        ChannelHandler ch = channel.pipeline().first();
        if (ch instanceof IdleStateHandler)
        {
            channel.pipeline().removeFirst();
        }
        channel.pipeline().addFirst(new IdleStateHandler(readerIdleTime, writerIdleTime, allIdleTime, unit));
    }

    /**
     * 计算读idle
     *
     * @return
     */
    public long readerIdle()
    {
        ChannelHandler ch = channel.pipeline().first();
        if (ch instanceof IdleStateHandler)
        {
            IdleStateHandler ish = (IdleStateHandler) ch;
            return ish.getReaderIdleTimeInMillis();
        }
        return 0;
    }

    /**
     * 计算写idle
     *
     * @return
     */
    public long writerIdle()
    {
        ChannelHandler ch = channel.pipeline().first();
        if (ch instanceof IdleStateHandler)
        {
            IdleStateHandler ish = (IdleStateHandler) ch;
            return ish.getWriterIdleTimeInMillis();
        }
        return 0;
    }

    /**
     * 计算all idle
     *
     * @return
     */
    public long allIdle()
    {
        ChannelHandler ch = channel.pipeline().first();
        if (ch instanceof IdleStateHandler)
        {
            IdleStateHandler ish = (IdleStateHandler) ch;
            return ish.getAllIdleTimeInMillis();
        }
        return 0;
    }
}
