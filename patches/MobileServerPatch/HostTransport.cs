using System;
using System.Collections.Concurrent;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Threading;
using System.Threading.Channels;
using System.Threading.Tasks;
using Terraria.Net;
using Terraria.Net.Sockets;

namespace MobileServerPatch;

// Only byte transport runs on workers. Player allocation, packet parsing and
// disconnect hooks all run on the game thread, including accepted connections.
internal sealed class HostTransport : IDisposable
{
	private readonly TcpListener listener;
	private readonly CancellationTokenSource cancellation = new();
	private readonly ConcurrentQueue<TcpClient> accepted = new();
	private readonly ConcurrentQueue<(SocketSendCallback Callback, object State)> completions = new();
	private readonly Task acceptTask;

	internal HostTransport(int port)
	{
		listener = new TcpListener(IPAddress.Any, port);
		listener.Server.ExclusiveAddressUse = true;
		listener.Start(); // Bind synchronously: a busy port cannot produce a half-started room.
		acceptTask = AcceptLoop();
	}

	private async Task AcceptLoop()
	{
		try {
			while (!cancellation.IsCancellationRequested)
				accepted.Enqueue(await listener.AcceptTcpClientAsync(cancellation.Token).ConfigureAwait(false));
		}
		catch (OperationCanceledException) { }
		catch (SocketException) when (cancellation.IsCancellationRequested) { }
		catch (ObjectDisposedException) when (cancellation.IsCancellationRequested) { }
	}

	internal bool TryAccept(out ISocket socket)
	{
		if (acceptTask.IsFaulted)
			throw new IOException("The room listener failed.", acceptTask.Exception);
		if (accepted.TryDequeue(out TcpClient client)) {
			socket = new PeerSocket(client, CompleteSend);
			return true;
		}
		socket = null;
		return false;
	}

	public void Dispose()
	{
		cancellation.Cancel();
		listener.Stop();
		try { acceptTask.GetAwaiter().GetResult(); }
		catch (Exception) { /* The owner already reports listener faults; cleanup must continue. */ }
		finally {
			while (accepted.TryDequeue(out TcpClient client))
				client.Dispose();
			cancellation.Dispose();
		}
	}

	internal void CompleteSend(SocketSendCallback callback, object state) => completions.Enqueue((callback, state));
	internal void DrainCompletions()
	{
		while (completions.TryDequeue(out var completion))
			completion.Callback?.Invoke(completion.State);
	}

	private sealed class PeerSocket : ISocket
	{
		private readonly TcpClient client;
		private readonly RemoteAddress address;
		private readonly Channel<byte[]> sends = Channel.CreateUnbounded<byte[]>(new UnboundedChannelOptions { SingleReader = true });
		private readonly CancellationTokenSource cancellation = new();
		private readonly Task sendTask;
		private readonly Action<SocketSendCallback, object> complete;
		private int queuedBytes;
		private int closed;

		internal PeerSocket(TcpClient client, Action<SocketSendCallback, object> complete)
		{
			this.client = client;
			this.complete = complete;
			client.NoDelay = true;
			var endpoint = (IPEndPoint)client.Client.RemoteEndPoint;
			address = new TcpAddress(endpoint.Address, endpoint.Port);
			sendTask = SendLoop();
		}

		private async Task SendLoop()
		{
			try {
				NetworkStream stream = client.GetStream();
				await foreach (byte[] bytes in sends.Reader.ReadAllAsync(cancellation.Token).ConfigureAwait(false)) {
					await stream.WriteAsync(bytes, cancellation.Token).ConfigureAwait(false);
					Interlocked.Add(ref queuedBytes, -bytes.Length);
				}
			}
			catch (Exception ex) when (ex is IOException or SocketException or OperationCanceledException or ObjectDisposedException) {
				Close();
			}
		}

		public bool IsConnected()
		{
			if (Volatile.Read(ref closed) != 0)
				return false;
			try { return !(client.Client.Poll(0, SelectMode.SelectRead) && client.Available == 0); }
			catch (Exception ex) when (ex is SocketException or ObjectDisposedException) { return false; }
		}

		public void Close()
		{
			if (Interlocked.Exchange(ref closed, 1) != 0)
				return;
			sends.Writer.TryComplete();
			cancellation.Cancel();
			client.Dispose();
		}

		public void AsyncSend(byte[] data, int offset, int size, SocketSendCallback callback, object state = null)
		{
			if (!IsConnected())
				return;
			// Copy: NetMessage immediately reuses its serializer buffer.
			if (Interlocked.Add(ref queuedBytes, size) > 16 * 1024 * 1024) {
				Close();
				return;
			}
			byte[] copy = data.AsSpan(offset, size).ToArray();
			if (sends.Writer.TryWrite(copy))
				complete(callback, state); // Defer recycling until every broadcast peer has copied the packet.
		}

		public bool IsDataAvailable() => IsConnected() && client.Available > 0;
		public void AsyncReceive(byte[] data, int offset, int size, SocketReceiveCallback callback, object state = null)
		{
			// Called only after IsDataAvailable. Never leave a receive callback that
			// could write into a new session's MessageBuffer after this room closes.
			int available = client.Available;
			if (available > 0)
				callback(state, client.Client.Receive(data, offset, Math.Min(available, size), SocketFlags.None));
			else
				callback(state, 0);
		}
		public RemoteAddress GetRemoteAddress() => address;
		public void SendQueuedPackets() { }
		public void Connect(RemoteAddress remote) => throw new NotSupportedException();
		public bool StartListening(SocketConnectionAccepted callback) => throw new NotSupportedException();
		public void StopListening() { }
	}
}

// A reserved server-side slot for the player already sharing this world.
// Packets addressed to it must not be parsed back into that same world.
internal sealed class LocalHostSocket : ISocket
{
	private readonly Action<SocketSendCallback, object> complete;
	internal LocalHostSocket(Action<SocketSendCallback, object> complete) => this.complete = complete;
	public void Close() { }
	public bool IsConnected() => HostSession.IsHosting;
	public RemoteAddress GetRemoteAddress() => new TcpAddress(IPAddress.Loopback, Terraria.Netplay.ListenPort);
	public void AsyncSend(byte[] data, int offset, int size, SocketSendCallback callback, object state = null) => complete(callback, state);
	public bool IsDataAvailable() => false;
	public void AsyncReceive(byte[] data, int offset, int size, SocketReceiveCallback callback, object state = null) => throw new NotSupportedException();
	public void SendQueuedPackets() { }
	public void Connect(RemoteAddress address) => throw new NotSupportedException();
	public bool StartListening(SocketConnectionAccepted callback) => throw new NotSupportedException();
	public void StopListening() { }
}
