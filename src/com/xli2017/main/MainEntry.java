package com.xli2017.main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;

public class MainEntry
{
	public static final int ITERATION = 1000;
	public static final int DATA_LENGTH = 740; // Need to be equal or bigger than DISPLAY_DIGITS_LIMIT
	public static final int DISPLAY_DIGITS_LIMIT = 10; // Need to be equal or smaller than DATA_LENGTH
	public static final int SEND_TIME_STEP = 30; // [ms]
	public static final int RECEIVE_CHECK_TIME_STEP = 15; // [ms]
	/** Pipe 0 for transmitting data between image capture thread and image process thread */
	public static Pipe pipe_0;
	public static Pipe.SinkChannel sinkChannel_0;
	public static Pipe.SourceChannel sourceChannel_0;
	
	public static SendThread sendThread;
	public static ReceiveThread receiveThread;
	public static ReceiveThread receiveThread_2;
	
	public static int[] counter;
	
	public MainEntry()
	{
		/* Open the pipe 0 */
		try
		{
			pipe_0 = Pipe.open();
			/* Set the sink and source pipe to blocking mode */
			pipe_0.sink().configureBlocking(false);
			pipe_0.source().configureBlocking(false);
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		sinkChannel_0 = pipe_0.sink();
		sourceChannel_0 = pipe_0.source();
		
		MainEntry.sendThread = new SendThread();
		MainEntry.receiveThread = new ReceiveThread();
		MainEntry.receiveThread_2 = new ReceiveThread();
		
		counter = new int[3];
	}
	
	public void run()
	{
		MainEntry.sendThread.start();
		MainEntry.receiveThread.start();
		MainEntry.receiveThread_2.start();
	}
	
	public class SendThread extends Thread
	{
		private ByteBuffer buf;
		
		public SendThread()
		{
			this.buf = ByteBuffer.allocateDirect(10000);
		}
		
		@Override
		public void run()
		{
			/* Get current thread name */
			Thread t = Thread.currentThread();
		    String tName = t.getName();
		    int executingTimes = 0;
		    
		    
			while(executingTimes < MainEntry.ITERATION)
			{
				executingTimes++;
				int[] intArray = generateIntArray();
				
				/* Output stream for concatenating */
				ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
				for (int i = 0; i < intArray.length; i++)
				{
					try
					{
						if (intArray[i] < 10)
						{
							outputStream.write(48); // 48 --> 0
						}
						outputStream.write(Integer.toString(intArray[i]).getBytes()); // Header length
					}
					catch (IOException e)
					{
						e.printStackTrace();
					}
				}
				
				/* Byte array going to be returned */
				byte[] intInByte = outputStream.toByteArray();
				
				System.out.println(executingTimes);
				
				System.out.print("--> " + tName + " sent\t" + intInByte.length + " bytes, the front " 
				+ MainEntry.DISPLAY_DIGITS_LIMIT + " bytes are: ");
				for (int i = 0; (i < intInByte.length) && (i < MainEntry.DISPLAY_DIGITS_LIMIT); i++)
				{
					System.out.print(intInByte[i]);
					System.out.print("\t");
				}
				System.out.println();
				
				System.out.print("--> " + "The numbers are: ");
				for (int i = 0; (i < intArray.length) && (i < MainEntry.DISPLAY_DIGITS_LIMIT/2); i++)
				{
					System.out.print(intArray[i]);
					System.out.print("\t");
				}
				System.out.println();
				
				try
				{
					outputStream.close();
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				
				if(intInByte != null)
				{
					/* Use the buffer to store the data that going to be sent */
					this.buf.clear();
					this.buf.put(intInByte);
					/* Flip so that the buffer index can get ready for sending */
					this.buf.flip();
					
					while(this.buf.hasRemaining())
					{
					    try
					    {
							MainEntry.sinkChannel_0.write(this.buf);
						}
					    catch (IOException e)
					    {
							e.printStackTrace();
						}
					}
					this.buf.compact();
					MainEntry.counter[0]++;
				}
				
				try
				{
					Thread.sleep(MainEntry.SEND_TIME_STEP);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
			
			System.out.println("Sent: " + executingTimes + ";\n" 
			+ "Thread-0: " + MainEntry.counter[0]
					+ ";\n" + "Thread-1: " + MainEntry.counter[1]
							+ ";\n" + "Thread-2: " + MainEntry.counter[2]);
			System.exit(0);
		}
		
		private int[] generateIntArray()
		{
			int[] intArray = new int[MainEntry.DATA_LENGTH];
			for (int i = 0; i < intArray.length; i++)
			{
				intArray[i] = (int) Math.floor(100*Math.random());
			}
			return intArray;
		}
	}
	
	public class ReceiveThread extends Thread
	{
		private ByteBuffer buf;
		
		public ReceiveThread()
		{
			this.buf = ByteBuffer.allocateDirect(10000);
		}
		
		@Override
		public void run()
		{
			byte[] intInByte = null;
			/* Get current thread name */
			Thread t = Thread.currentThread();
		    String tName = t.getName();
		    int threadIndex = 0;
		    switch (tName)
		    {
		    case "Thread-1":
		    	threadIndex = 1;
		    	break;
		    case "Thread-2":
		    	threadIndex = 2;
		    	break;
		    	default:
		    		System.out.println("The name of thread is wrong.");
		    		System.exit(0);
		    		break;
		    }
		    
			while(true)
			{
				try
				{
					/* Try to read data from pipe */
					intInByte = this.readPipe(MainEntry.sourceChannel_0); // This is a synchronized method
				}
				catch (IOException e)
				{
					e.printStackTrace();
				}
				
				if (intInByte != null) // New data comes
				{
					System.out.print("[_] " + tName + " receive\t" + intInByte.length + " bytes, the front "
							+ MainEntry.DISPLAY_DIGITS_LIMIT + " bytes are: ");
					for (int i = 0; (i < intInByte.length) && (i < MainEntry.DISPLAY_DIGITS_LIMIT); i++)
					{
						System.out.print(intInByte[i]);
						System.out.print("\t");
					}
					System.out.println();
					
					System.out.print("[_] " + "The numbers are: ");
					for (int i = 0; (i < intInByte.length) && (i < MainEntry.DISPLAY_DIGITS_LIMIT); i = i+2)
					{
						byte[] twoDigitsByte = {intInByte[i], intInByte[i+1]};
						System.out.print(IntFromDecimalAscii(twoDigitsByte));
						System.out.print("\t");
					}
					System.out.println();
					System.out.println();
					MainEntry.counter[threadIndex]++;
				}
				
				try
				{
					Thread.sleep(MainEntry.RECEIVE_CHECK_TIME_STEP);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
		
		/**
		 * Read the source pipe
		 * @param sourceChannel Source channel of a pipe where data comes
		 * @return An byte array that stores the data if there is new data; null for no new data
		 * @throws IOException
		 */
		private synchronized byte[] readPipe(Pipe.SourceChannel sourceChannel) throws IOException
		{
			/* How many bytes has been read into the buffer */
			int bytesRead = sourceChannel.read(this.buf);
			
			if (bytesRead > 0) // New data available
			{
//				/* Get current thread name */
//				Thread t = Thread.currentThread();
//			    String tName = t.getName();
//				System.out.println(tName + " readPipe method read bytes: " + bytesRead);
				byte[] intInByte = new byte[bytesRead];
				int index = 0; // The index of intInByte
				this.buf.flip();
				while(this.buf.hasRemaining())
				{
					intInByte[index] = this.buf.get();
					index++;
				}
				this.buf.clear();
				return intInByte;
			}
			else // No new data
			{
				return null;
			}
		}
		
		/**
		 * One implementation for converting decimal numbers
		 * @author Jason Watkins
		 * @param bytes byte array need to be converted
		 * @return integer
		 */
		private int IntFromDecimalAscii(byte[] bytes)
		{
		    int result = 0;

		    // For each digit, add the digit's value times 10^n, where n is the
		    // column number counting from right to left starting at 0.
		    for(int i = 0; i < bytes.length; ++i)
		    {
		        // ASCII digits are in the range 48 <= n <= 57. This code only
		        // makes sense if we are dealing exclusively with digits, so
		        // throw if we encounter a non-digit character
		        if(bytes[i] < 48 || bytes[i] > 57)
		        {
		            System.out.println("Non-digit character present: " + bytes[i]);
		            return -1;
		        }

		        // The bytes are in order from most to least significant, so
		        // we need to reverse the index to get the right column number
		        int exp = bytes.length - i - 1;

		        // Digits in ASCII start with 0 at 48, and move sequentially
		        // to 9 at 57, so we can simply subtract 48 from a valid digit
		        // to get its numeric value
		        int digitValue = bytes[i] - 48;

		        // Finally, add the digit value times the column value to the
		        // result accumulator
		        result += digitValue * (int)Math.pow(10, exp);
		    }

		    return result;
		}
	}
}
