package com.xli2017.main;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.logging.Level;

public class MainEntry
{
	/** Pipe 0 for transmitting data between image capture thread and image process thread */
	public static Pipe pipe_0;
	public static Pipe.SinkChannel sinkChannel_0;
	public static Pipe.SourceChannel sourceChannel_0;
	
	public static SendThread sendThread;
	public static ReceiveThread receiveThread;
	
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
	}
	
	public void run()
	{
		MainEntry.sendThread.start();
		MainEntry.receiveThread.start();
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
		    
			while(true)
			{
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
				System.out.print("--> " + tName + " sent " + intInByte.length + " bytes: ");
				for (int i = 0; i < intInByte.length; i++)
				{
					System.out.print(intInByte[i]);
					System.out.print("\t");
				}
				System.out.println();
				
				System.out.print("--> " + "The number is: ");
				for (int i = 0; i < intArray.length; i++)
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
				}
				
				try
				{
					Thread.sleep(300);
				}
				catch (InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
		
		private int[] generateIntArray()
		{
			int[] intArray = new int[10];
			for (int i = 0; i < 10; i++)
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
					System.out.print("[_] " + tName + " received " + intInByte.length + " bytes: ");
					for (int i = 0; i < intInByte.length; i++)
					{
						System.out.print(intInByte[i]);
						System.out.print("\t");
					}
					System.out.println();
					
					System.out.print("[_] " + "The number is: ");
//					System.out.print(IntFromDecimalAscii(intInByte));
					for (int i = 0; i < intInByte.length; i = i+2)
					{
						byte[] twoDigitsByte = {intInByte[i], intInByte[i+1]};
						System.out.print(IntFromDecimalAscii(twoDigitsByte));
						System.out.print("\t");
					}
					System.out.println();
					System.out.println();
				}
				
				try
				{
					Thread.sleep(100);
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
