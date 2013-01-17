package com.lurencun.imageloader;

import java.util.Iterator;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author : 桥下一粒砂
 * @email : chenyoca@gmail.com
 * @date : 2012-10-22
 * @desc : 基于Executor框架的线程池管理器
 */
public class ThreadPoolManager {
	
	public static boolean DEBUG = true;

	public final static int INALID_REQUEST = -1;
	
	public final static int CORE_POOL_SIZE = 3;
	public final static int MAX_POOL_SIZE = CORE_POOL_SIZE * 3;
	public final static int KEEP_ALIVE_TIME_IN_S = 3 * 10;
	private final static int TASK_SCHEDULE_DELAY = 6 * 1000;
	
	private ConcurrentHashMap<Integer, Future<?>> taskHolder;
	private int requestTaskIdPool;
	private final ThreadPoolExecutor taskExecutor ;
	private final Timer cleanNullReferenceDaemon;
	
	private final ExecutorService submitRequestProxy = Executors.newFixedThreadPool(2);
	
	{
		SynchronousQueue<Runnable> queue = new SynchronousQueue<Runnable>();
		taskExecutor  = new ThreadPoolExecutor(
				CORE_POOL_SIZE, MAX_POOL_SIZE, KEEP_ALIVE_TIME_IN_S, TimeUnit.SECONDS,
				queue, new ThreadPoolExecutor.CallerRunsPolicy());
		cleanNullReferenceDaemon = new Timer(true);
		taskHolder = new ConcurrentHashMap<Integer, Future<?>>();
		// 开启空引用自检任务
		cleanNullReferenceDaemon.schedule(new TimerTask() {
			@Override
			public void run() {
				Iterator<Map.Entry<Integer, Future<?>>> iter = taskHolder.entrySet().iterator();
//				System.err.println("【INFO】~ [Thread Pool Manager] " +
//						"{ TaskCount:"+taskExecutor.getTaskCount()+ ", PoolSize:"+taskExecutor.getPoolSize()+ " }");
				while (iter.hasNext()) {
					Map.Entry<Integer, Future<?>> entry = (Map.Entry<Integer, Future<?>>) iter.next();
					Future<?> task = entry.getValue();
					int key = entry.getKey();
					if(task.isDone() || task.isCancelled()){
						taskHolder.remove(key);
//						if(DEBUG){
//							System.err.println("【INFO】~ [Thread Pool Deamon ] Remove task reference { id:"+key+" }");
//						}
					}
				}
			}
		}, 0,TASK_SCHEDULE_DELAY);
	}
	
	/**
	 * 提交一个任务。由代理线程池向主执行线程池提交，实现本方法的快速返回。
	 * @param r Runnable实现
	 * @return 一个任务包装对象
	 */
	public int submit(final Runnable r){
		final int taskId = requestTaskIdPool++;
		submitRequestProxy.execute(new Runnable(){
			@Override
			public void run() {
				Future<?> task = taskExecutor.submit(r);
				taskHolder.put(taskId, task);
			}
		});
		return taskId;
	}
	
	/**
	 * 取消某个线程任务
	 * @param taskId 任务ID
	 * @param interruptRunning 是否中断任务
	 */
	public void cancel(int taskId,boolean interruptRunning){
		if(taskHolder.containsKey(taskId)){
			Future<?> task = taskHolder.get(taskId);
			if(!task.isDone() && !task.isCancelled()){
				task.cancel(interruptRunning);
			}
		}
	}
	
	/**
	 *  取消某个线程任务，如果任务正在执行，则中断运行。
	 * @param taskId
	 */
	public void cancel(int taskId){
		cancel(taskId,true);
	}
	
	/**
	 * 销毁线程池
	 */
	public void destory(){
		taskExecutor.shutdownNow();
	}
}
