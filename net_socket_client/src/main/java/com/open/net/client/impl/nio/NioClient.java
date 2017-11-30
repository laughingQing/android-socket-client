package com.open.net.client.impl.nio;

import com.open.net.client.impl.nio.processor.ConnectProcessor;
import com.open.net.client.listener.IConnectStatusListener;
import com.open.net.client.structures.BaseClient;
import com.open.net.client.structures.BaseMessageProcessor;
import com.open.net.client.structures.TcpAddress;
import com.open.net.client.structures.message.Message;

/**
 * author       :   long
 * created on   :   2017/11/30
 * description  :   NioClient
 */

public class NioClient extends BaseClient{

    private final String TAG="NioClient";

    private TcpAddress[] tcpArray   = null;
    private int          index      = -1;

    private ConnectProcessor mConnectProcessor = null;
    private Thread mConnectProcessorThread = null;

    private BaseMessageProcessor    mMessageProcessor = null;
    private IConnectStatusListener  mConnectStatusListener = null;

    public NioClient(TcpAddress[] tcpArray, BaseMessageProcessor mMessageProcessor, IConnectStatusListener mConnectStatusListener) {
        this.tcpArray = tcpArray;
        this.mMessageProcessor = mMessageProcessor;
        this.mConnectStatusListener = mConnectStatusListener;
    }

    //-------------------------------------------------------------------------------------------
    public void setConnectAddress(TcpAddress[] tcpArray ){
        this.tcpArray = tcpArray;
    }

    //-------------------------------------------------------------------------------------------
    public void sendMessage(Message msg){
        //1.没有连接,需要进行重连
        //2.在连接不成功，并且也不在重连中时，需要进行重连;
        if(null == mConnectProcessor){
            addWriteMessage(msg);
            startConnect();
        }else if(!mConnectProcessor.isConnected() && !mConnectProcessor.isConnecting()){
            addWriteMessage(msg);
            startConnect();
        }else{
            addWriteMessage(msg);
            if(mConnectProcessor.isConnected()){
                mConnectProcessor.wakeUp();
            }else{
                //说明正在重连中
            }
        }
    }

    //-------------------------------------------------------------------------------------------
    public synchronized void connect() {
        startConnect();
    }

    public synchronized void reconnect(){
        stopConnect(true);
        //reset the ip/port index of tcpArray
        if(index+1 >= tcpArray.length || index+1 < 0){
            index = -1;
        }
        startConnect();
    }

    public synchronized void disconnect(){
        stopConnect(true);
    }

    private synchronized void startConnect(){
        //已经在连接中就不再进行连接
        if(null != mConnectProcessor && !mConnectProcessor.isClosed()){
            return;
        }

        index++;
        if(index < tcpArray.length && index >= 0){
            stopConnect(false);
            mConnectProcessor = new ConnectProcessor(this, tcpArray[index].ip,tcpArray[index].port, mConnectStatusListener, mMessageProcessor);
            mConnectProcessorThread =new Thread(mConnectProcessor);
            mConnectProcessorThread.start();
        }else{
            index = -1;

            //循环连接了一遍还没有连接上，说明网络连接不成功，此时清空消息队列，防止队列堆积
            super.clear();
        }
    }

    private synchronized void stopConnect(boolean isCloseByUser){
        try {

            if(null != mConnectProcessor) {
                mConnectProcessor.setCloseByUser(isCloseByUser);
                mConnectProcessor.close();
            }
            mConnectProcessor = null;

            if( null!= mConnectProcessorThread && mConnectProcessorThread.isAlive() ) {
                mConnectProcessorThread.interrupt();
            }
            mConnectProcessorThread =null;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
