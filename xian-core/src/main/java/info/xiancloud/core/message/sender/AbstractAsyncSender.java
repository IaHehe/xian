package info.xiancloud.core.message.sender;

import info.xiancloud.core.NotifyHandler;
import info.xiancloud.core.message.IdManager;
import info.xiancloud.core.message.UnitRequest;
import info.xiancloud.core.message.UnitResponse;
import info.xiancloud.core.NotifyHandler;
import info.xiancloud.core.util.LOG;
import info.xiancloud.core.util.StringUtil;
import info.xiancloud.core.util.thread.MsgIdHolder;

/**
 * 消息发送器 <br>
 * 原型模式 : 每个新的消息发送均构造一个发送器。
 * 原静态Xian其实就是典型的单例模式,单例模式的缺点就是方法参数过多,传来传去,代码复杂度提高,扩展性差.
 *
 * @author happyyangyuan
 */
public abstract class AbstractAsyncSender implements IAsyncSender {

    protected final UnitRequest unitRequest;
    private final SenderFuture senderFuture;
    protected final NotifyHandler callback;

    protected AbstractAsyncSender(UnitRequest request, NotifyHandler handler) {
        senderFuture = new SenderFuture();
        unitRequest = request;
        callback = handler == null ? new NotifyHandler() {
            protected void handle(UnitResponse unitResponse) {
                //既然你不需要callback,那么这里什么也不用做啰
            }
        } : handler;
        callback.setAsync(handler != null);
        callback.addBefore(new NotifyHandler.Action() {
            protected void run(UnitResponse asyncUnitResponse) {
                senderFuture.setUnitResponse(asyncUnitResponse);
            }
        });
    }

    public SenderFuture send() {
        final boolean newTransIdGenerated = IdManager.makeSureMsgId(unitRequest.getContext());
        try {
            if (StringUtil.isEmpty(unitRequest.getContext().getGroup())) {
                callback.callback(UnitResponse.failure(null, "group name is required!"));
            } else if (StringUtil.isEmpty(unitRequest.getContext().getUnit())) {
                callback.callback(UnitResponse.failure(null, "unit name is required!"));
            } else {
                asyncSend();
            }
        } catch (Throwable throwable) {
            //not needed really, just in case.
            LOG.error(throwable);
            callback.callback(UnitResponse.exception(throwable));
        } finally {
            if (newTransIdGenerated) {
                //退出时清空msgId:生没带来,死不带走
                //内部发起了unit调用,而且没有提供$msgId,因此这里清空
                unitRequest.getContext().setMsgId(null);
                MsgIdHolder.clear();
            }
        }
        return senderFuture;
    }

    /**
     * 执行异步发送动作，并且必须在执行完毕后进行callback操作 <br>
     * Execute some thing in asynchronous way, subclass implementations must invoke the callback.
     *
     * @throws Throwable dead code, no exception is thrown. But just in case.
     */
    protected abstract void asyncSend() throws Throwable;

}
