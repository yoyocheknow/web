package com.callke8.predialqueue;

import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.asteriskjava.manager.AuthenticationFailedException;
import org.asteriskjava.manager.ManagerConnection;
import org.asteriskjava.manager.ManagerConnectionFactory;
import org.asteriskjava.manager.ManagerEventListener;
import org.asteriskjava.manager.TimeoutException;
import org.asteriskjava.manager.event.HangupEvent;
import org.asteriskjava.manager.event.ManagerEvent;

import com.callke8.astutils.AstMonitor;
import com.callke8.utils.BlankUtils;

/**
 * 挂机监控事件,主要是用于挂机时,解除活动通道
 * 
 * @author hwz
 *
 */
public class HangUpMonitor extends Thread implements ManagerEventListener {
	
	private ManagerConnectionFactory factory;
	private ManagerConnection conn;
	private String state;
	private Log log = LogFactory.getLog(HangUpMonitor.class);

	public HangUpMonitor() {
		factory = new ManagerConnectionFactory(AstMonitor.getAstHost(),AstMonitor.getAstPort(),AstMonitor.getAstUser(),AstMonitor.getAstPass());
		conn = factory.createManagerConnection();
		conn.addEventListener(this);
	}
	
	@Override
	public void run() {
		
		try {
			Thread.sleep(5 * 1000);   //为了等待环境变量加载完毕,先休眠5秒
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		
		int i = 1;
		
		while(true) {
			
			state = BlankUtils.isBlank(conn)?null:conn.getState().toString();
			
			if(BlankUtils.isBlank(state) || !state.equalsIgnoreCase("CONNECTED")) {   //如果连接状态无法连接时,将重新连接一次
				
				log.info("挂机监控线程,Asterisk连接不成功!系统将重新连接!");
				
				try {
					if(state.equalsIgnoreCase("RECONNECTING")) {   //如果状态为 RECONNECTING 时，需要先 logoff ，然后再重新连接
						conn.logoff();
					};
					conn.login();
				} catch (IllegalStateException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (AuthenticationFailedException e) {
					e.printStackTrace();
				} catch (TimeoutException e) {
					e.printStackTrace();
				}
				
			}
			
			if(i == 10){
				i = 1;
			}else {
				i++;
			}
			
			try {
				//每3秒监控一次
				Thread.sleep(3 * 1000);   
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		
	}

	@Override
	public void onManagerEvent(ManagerEvent event) {
		
		//如果监控的事件为挂机事件时
		if(event instanceof HangupEvent) {
			
			//强制转为挂机事件
			HangupEvent hangupEvent = (HangupEvent)event;
			
			//挂机的通道
			String channel = hangupEvent.getChannel();
			
			
			//活动通道中是否存在挂机的通道
			boolean isExist = LaunchDialService.activeChannelList.contains(channel);
			
			if(isExist) {   //如果挂机的通道存在于自动外呼的活动通道中时,要将其移除
				if(LaunchDialService.activeChannelCount > 0) {   //返回之前,活动的通道数量减1
					LaunchDialService.activeChannelCount--;
				}
				LaunchDialService.activeChannelList.remove(channel);
				
				log.info("获取挂机事件,挂机通道信息为:" + channel + ",且挂机的通道存在于自动外呼的内存变量活动通道中,系统将从活动通道中移除,并将活动通道数量减去一个,移除后,活动通道的数量为：" + LaunchDialService.activeChannelCount);
			}
			
		}
		
	}

}
