package io.xol.chunkstories.net.packets;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Deque;

import io.xol.chunkstories.GameData;
import io.xol.chunkstories.api.exceptions.SyntaxErrorException;
import io.xol.chunkstories.client.net.ServerConnection;
import io.xol.chunkstories.server.net.ServerClient;
import io.xol.chunkstories.tools.ChunkStoriesLogger;
import io.xol.engine.math.HexTools;

//(c) 2015-2016 XolioWare Interactive
//http://chunkstories.xyz
//http://xol.io

public class PacketsProcessor
{
	//There is 2^15 possible packets
	static PacketType[] packetTypes = new PacketType[32768];

	public static void loadPacketsTypes()
	{
		//Loads *all* possible packets types
		Deque<File> packetsFiles = GameData.getAllFileInstances("./res/data/packetsTypes.txt");
		for (File f : packetsFiles)
		{
			loadPacketFile(f);
		}
	}

	private static void loadPacketFile(File f)
	{
		if (!f.exists())
			return;
		try (FileReader fileReader = new FileReader(f); BufferedReader reader = new BufferedReader(fileReader);)
		{

			String line = "";
			int ln = 0;
			while ((line = reader.readLine()) != null)
			{
				if (line.startsWith("#"))
				{
					// It's a comment, ignore.
				}
				else
				{
					if (line.startsWith("packet"))
					{
						String splitted[] = line.split(" ");
						if (!splitted[0].equals("packet"))
							throw new SyntaxErrorException(ln, f, "no packet line");
						short packetId = (short) HexTools.parseHexValue(splitted[1]);
						String packetName = splitted[2];
						try
						{
							Class<?> untypedClass = Class.forName(splitted[3]);
							if (!Packet.class.isAssignableFrom(untypedClass))
								throw new SyntaxErrorException(ln, f, splitted[3] + " is not a subclass of Packet");
							Class<Packet> packetClass = (Class<Packet>) untypedClass;

							Class[] types = { Boolean.TYPE };
							Constructor<Packet> constructor = packetClass.getConstructor(types);

							String allowed = splitted[4];

							PacketType packetType = new PacketType(packetId, packetName, packetClass, constructor, !allowed.equals("server"), !allowed.equals("client"));
							packetTypes[packetId] = packetType;
							System.out.println(packetId+" "+packetName);
						}
						catch (ClassNotFoundException | NoSuchMethodException | SecurityException | IllegalArgumentException e)
						{
							e.printStackTrace();
						}
					}
				}
				ln++;
			}
			//reader.close();
		}
		catch (IOException | SyntaxErrorException e)
		{
			ChunkStoriesLogger.getInstance().warning(e.getMessage());
		}
	}

	static class PacketType
	{
		short id;
		String packetName;
		Class<Packet> packetClass;
		Constructor<Packet> packetConstructor;
		boolean clientCanSendIt = true;
		boolean serverCanSendIt = true;

		public PacketType(short id, String packetName, Class<Packet> packetClass, Constructor<Packet> packetConstructor, boolean clientCanSendIt, boolean serverCanSendIt)
		{
			super();
			this.id = id;
			this.packetName = packetName;
			this.packetClass = packetClass;
			this.packetConstructor = packetConstructor;
			this.clientCanSendIt = clientCanSendIt;
			this.serverCanSendIt = serverCanSendIt;
		}

		public Packet createNew(boolean isClient) throws IllegalPacketException
		{
			Object[] parameters = { isClient };
			try
			{
				Packet packet = (Packet) packetConstructor.newInstance(parameters);
				return packet;
			}
			catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e)
			{
				ChunkStoriesLogger.getInstance().warning("Failed to instanciate "+packetName);
				e.printStackTrace();
			}
			return null;
		}
	}

	//Both clients and server use this class
	ServerConnection serverConnection;
	ServerClient serverClient;
	boolean isClient = false;

	public PacketsProcessor(ServerConnection serverConnection)
	{
		this.serverConnection = serverConnection;
		isClient = true;
	}

	public PacketsProcessor(ServerClient serverClient)
	{
		this.serverClient = serverClient;
		isClient = false;
	}

	/**
	 * Read 1 or 2 bytes to get the next packet ID and returns a packet of this type if it exists
	 * 
	 * @param in
	 *            The InputStream of the connection
	 * @param client
	 *            Wether a client or a server has to process this packet
	 * @param sending
	 *            Wether we are receiving or sending this packet
	 * @return A valid Packet
	 * @throws IOException
	 *             If the stream dies when we process it
	 * @throws UnknowPacketException
	 *             If the packet id we obtain is invalid
	 * @throws IllegalPacketException
	 *             If the packet we obtain is illegal ( if we're not supposed to receive or send it )
	 */
	public Packet getPacket(DataInputStream in, boolean client, boolean sending) throws IOException, UnknowPacketException, IllegalPacketException
	{
		int firstByte = in.readByte();
		int packetType = 0;
		//If it is under 127 unsigned it's a 1-byte packet [0.firstByte(1.7)]
		if ((firstByte & 0x80) == 0)
			packetType = firstByte;
		else
		{
			//It's a 2-byte packet [0.firstByte(1.7)][secondByte(0.8)]
			int secondByte = in.readByte();
			secondByte = secondByte & 0xFF;
			packetType = secondByte | (firstByte & 0x7F) << 8;
		}
		Packet packet = packetTypes[packetType].createNew(client);

		if (packet == null)
			throw new UnknowPacketException(packetType);
		else
			return packet;
	}
}
