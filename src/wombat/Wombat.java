/*
 * wombat, the WOrry-free Multicore BATch-system
 * 
 * Copyright 2009 S. Berger
 * 
 *  This program is free software; you may redistribute it and/or modify its
 *  under the terms of the GNU General Public License as published by the Free
 *  Software Foundation; either version 2 of the License, or (at your option)
 *  any later version.
 */

package wombat;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.swing.JFrame;

import wombat.gui.Main;
import wombat.pack.TxExec;




class CfgThread extends Thread {

	final ThreadPoolExecutor tpe;
	final RunnableSet rs;
		
	
	CfgThread( ThreadPoolExecutor tpe, RunnableSet rs ) {
		this.tpe = tpe;
		this.rs = rs;
	}
	public void run() {
		BufferedReader stdin = new BufferedReader(new InputStreamReader(System.in));
		String line;
		try {
			while( !isInterrupted() && (line = stdin.readLine()) != null ) {
			//	System.out.printf( "read line '%s'\n", line );
				
				StringTokenizer st = new StringTokenizer(line);
				
				if( !st.hasMoreTokens() ) {
					continue;
				}
				
				String token = st.nextToken();
				
				if( token.equals("exit")) {
					System.exit(0);
				} else if( token.equals("ncores")) {
					if( !st.hasMoreTokens() ) {
						System.out.printf( "ncores: %d\n", tpe.getMaximumPoolSize() );
					} else {
						token = st.nextToken();
						int poolsize = Integer.parseInt(token);
						
						if( poolsize > 0 && poolsize < 32 ) {
							System.out.printf( "MaximumPoolSize set to %d\n", poolsize );
							if( poolsize > tpe.getMaximumPoolSize() ) {
								tpe.setMaximumPoolSize(poolsize);
							}
							tpe.setCorePoolSize(poolsize);
							if( tpe.getMaximumPoolSize() > poolsize ) {
								tpe.setMaximumPoolSize(poolsize);
							}
							
						} else {
							System.out.printf( "bad (?) number of cores: %d\n", poolsize );
						}
					}
				} else if( token.equals("pq")) {
					BlockingQueue<Runnable> q = tpe.getQueue();
					int i = 0;
					
					for( Runnable x : q ) {
						if( x instanceof MyRunnable ) {
							MyRunnable mx = (MyRunnable) x;
							System.out.printf( "%d: serial: %d cmd: '%s'\n", i, mx.getSerial(), mx.getCommand() );
						} else {
							System.out.printf( "%d: '%s'\n", i, q );
						}
						i++;
					}
				} else if( token.equals( "ps")) {
					List<MyRunnable> l = rs.getRunning();
					int i = 0;
					long timeNow = System.currentTimeMillis();
					
					boolean verbose = st.hasMoreTokens() && st.nextToken().equals("v");
					
					
					for( MyRunnable r : l ) {
						String cmd = r.getCommand();
						
						if( !verbose ) {
							cmd = "... " + cmd.substring(cmd.length() - 40);
						}
						
						long t = timeNow - r.startTime(); 
						String tstr;
						if( t < 60000 ) {
							tstr = t / 1000 + "s";
						} else if( t < 60000 * 60 ){
							tstr = t / 60000 + "m";
						} else if( t < 60000 * 60 * 24 ){
							tstr = t / (60000 * 60) + "h";
						} else {
							tstr = t / (60000 * 60 * 24) + "d";
						}
						
						System.out.printf( "%d: serial: %d time: %s cmd: '%s'\n", i, r.getSerial(), tstr, cmd );
						i++;
					}
				} else if( token.equals( "info")) {
					System.out.printf( "pool size: %d %d %d (max/core/actual)\n", tpe.getMaximumPoolSize(), tpe.getCorePoolSize(), tpe.getPoolSize() );
				
	
					System.out.printf( "num tasks: %d %d %d (active/completed/overall)\n", tpe.getActiveCount(), tpe.getCompletedTaskCount(), tpe.getTaskCount() );
					
					
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.out.printf( "cfg thread finished\n" );
	}

}

public class Wombat {
	private ThreadPoolExecutor tpe;
	final private File batchfile;
	RunnableSet rs = new RunnableSet();
	final Thread cfgThread;
	
//	Vector<Future<?>> submitted = new Vector<Future<?>>(); // Vector should be thread-safe...
	
	public Wombat(File batchfile, final int N_THREADS ) {
		this.batchfile = batchfile;
		
		tpe = (ThreadPoolExecutor) Executors.newFixedThreadPool(N_THREADS);
		
		//tpe = new ThreadPoolExecutor( N_THREADS, N_THREADS, 0L, TimeUnit.SECONDS, new ArrayBlockingQueue<Runnable>(N_THREADS * 3));
		//tpe.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
		
		
		cfgThread = new CfgThread( tpe, rs );
		cfgThread.start();
	}

	private void start() {
		
		Runtime.getRuntime().addShutdownHook( new Thread() {
			public void run() {
				synchronized (this) {
					System.out.printf( "enter exit handler\n" );
			
					cfgThread.interrupt();
					
					System.out.printf( "shutdown NOW!\n" );
					tpe.shutdownNow();
					System.out.printf( "shutdown NOW returned\n" );
				//	rs.killAll();
				//	System.out.printf( "kill all\n" );
					
					try {
						System.out.printf( "waiting ...\n" );
						tpe.awaitTermination(1, TimeUnit.DAYS);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					System.out.printf( "all threads/processes should be terminated now...\n" );
				}
			}
		});
		
		try {
			BufferedReader br = new BufferedReader(new FileReader(batchfile));
	
			String line;
			int serial = 1;
			
			while( ( line = br.readLine()) != null ) {
				if( line.length() == 0 || line.startsWith("#") ) {
					continue;
				} else if( line.startsWith( "%tx%" )) {
					System.out.printf( "start tx task: '%s'\n", line );
					Runnable txr = TxExec.newRunnable( line, serial, rs );
					
					tpe.execute(txr);
					serial++;
				} else {
					System.out.printf( "start task: '%s'\n", line );
					
					final ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", line );
					MyRunnable mr = new MyRunnable(pb, serial, rs);
				
					tpe.execute(mr);
//					submitted.add( f );
					serial++;
				}
				
			}
			
			System.out.printf( "done queuing. waiting for shutdown...\n" );
			tpe.shutdown();
			try {
				// one thousand years should be enough for anyone.
				tpe.awaitTermination( 1000 * 365, TimeUnit.DAYS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			System.out.printf( "exit!\n" );
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) throws IOException, InterruptedException {
		
		
		
		File batchfile;
		
		if( args.length > 0 ) {
			batchfile = new File( args[0] );
		} else {
			batchfile = new File( "/home/sim/test.wb");
		}
		
		final int N_THREADS;
		if( args.length > 1 ) {
			N_THREADS = Integer.parseInt(args[1]);
		} else {
			N_THREADS = 2;
		}
		
//		JFrame jf = new JFrame();
//		jf.getContentPane().add( new Main() );
//		jf.setVisible(true);
		
		Wombat wb = new Wombat( batchfile, N_THREADS );
		wb.start();
		
		
		
		System.exit(0);
		
//		final ProcessBuilder pb = new ProcessBuilder("/bin/bash", "-c", "/home/sim/busy");
//		pb.environment().put( "BLA", "XTC");
//		
//		MyRunnable mr = new MyRunnable(pb, 666, null);
//		Thread t = new Thread( mr );
//		
//		t.start();
//		System.out.printf( "go to sleep\n" );
//		long time = System.currentTimeMillis();
//		long wakeetime = time + 10000;
//		
//		while( (time = System.currentTimeMillis()) < wakeetime ) {
//			Thread.currentThread().sleep(wakeetime - time);
//			System.out.printf( "yaaawn\n" );
//		}
//		
//		System.out.printf( "wakeee up!\n" );
//		mr.killProcess();
//		
//		t.join();
	
		
	}

	
}