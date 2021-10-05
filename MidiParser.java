package random;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

/** 
Turns the midi file into a usable class. 
Has several subclasses for storing individual chunks and reading data from them.
Made by MrDuck557
Follows this specification (http://www.somascape.org/midi/tech/mfile.html) to the best of my knowledge
*/

public class MidiParser {
	FileInputStream file;

	public MidiParser(File f) throws IOException { // starts reading from f
		file = new FileInputStream(f);
	}

	public Chunk nextChunk() throws IOException {
		return Chunk.makeChunk(file);
	}

	public static class Chunk { // a single chunk

		static Chunk makeChunk(FileInputStream f) throws IOException { // reads entire chunk and outputs
			String id = "";
			int test = f.read();
			if (test == -1) {
				return null;
			}
			id += (char) test;
			for (int x = 0; x < 3; x++) {
				id += (char) f.read();
			}
			if (id.equals("MThd")) {
				return HeaderChunk.makeChunk(f);
			} else if (id.equals("MTrk")) {
				return TrackChunk.makeChunk(f);
			}
			return InvalidChunk.makeChunk(f);
		}
	}

	public static class HeaderChunk extends Chunk { // a header chunk
		int format;
		int ntracks;
		int tickdiv;

		public int get() {
			return ntracks;
		}

		static HeaderChunk makeChunk(FileInputStream f) throws IOException {
			int[] temp = new int[4];
			int chunkLen;
			for (int x = 0; x < 4; x++) {
				temp[x] = f.read();
			}
			HeaderChunk out = new HeaderChunk();
			chunkLen = fromByteArray(temp) - 6;
			temp = new int[4];
			temp[2] = f.read();
			temp[3] = f.read();
			out.format = fromByteArray(temp);
			temp[2] = f.read();
			temp[3] = f.read();
			out.ntracks = fromByteArray(temp);
			temp[2] = f.read();
			temp[3] = f.read();
			out.tickdiv = fromByteArray(temp);
			for (int x = 0; x < chunkLen; x++) { // if there is anything else, read rest of chunk
				f.read();
			}
			return out;
		}

		public String toString() {
			return "Header:\n  Format: " + format + "\n  Tracks: " + ntracks + "\n  Tickdiv: " + tickdiv;
		}
	}

	public static class TrackChunk extends Chunk { //a track chunk

		ArrayList<Event> events;
		int index;

		static TrackChunk makeChunk(FileInputStream f) throws IOException {
			int[] temp = new int[4];
			int chunkLen;
			for (int x = 0; x < 4; x++) {
				temp[x] = f.read();
			}
			chunkLen = fromByteArray(temp);
			TrackChunk out = new TrackChunk();
			out.events = new ArrayList<Event>();
			Event cur;
			while (chunkLen > 0) {
				cur = new Event(f);
				chunkLen -= cur.len;
				out.events.add(cur);
			}
			out.index = -1;
			return out;
		}
		
		public Event nextEvent() {
			index++;
			if (index>=events.size()) {
				return null;
			}
			return events.get(index);
		}

		public String toString() {
			String out = "Track:\n";
			for (Event x : events) {
				out += x.toString() + "\n";
			}
			return out;
		}

		public static class Event {
			int delta;
			int type;
			int channel;
			int len;
			int[] data;

			Event(FileInputStream f) throws IOException {
				int[] temp = varLengthRead(f);
				len = temp[1];
				delta = temp[0];
				int bte = f.read();
				len++;
				type = bte >> 4;
				channel = bte & 15;
				if (type >= 8 && type <= 14) { // midi events
					switch (type) {
					case 12:
						data = new int[1];
						data[0] = f.read();
						break;
					case 13:
						data = new int[1];
						data[0] = f.read();
						break;
					default:
						data = new int[2];
						data[0] = f.read();
						data[1] = f.read();
						len++;
					}
					len++;
				} else if (type == 15 && channel != 15) { // sysex events
					int loop;
					temp = varLengthRead(f);
					loop = temp[0];
					len += temp[1];
					data = new int[loop];
					for (int x = 0; x < loop; x++) {
						data[x] = f.read();
						len++;
					}
				} else if (type == 15 && channel == 15) { // meta events
					int loop;
					int messType = f.read();
					len++;
					temp = varLengthRead(f);
					loop = temp[0];
					len += temp[1];
					data = new int[loop + 1];
					data[0] = messType;
					for (int x = 1; x <= loop; x++) {
						data[x] = f.read();
						len++;
					}
				} // all events should go into an if block
			}

			public String toString() {
				String out = "  Event:\n";
				out += "    Delta: " + delta + "\n";
				out += "    Type: " + type + "\n";
				out += "    Channel: " + channel + "\n";
				out += "    Data:\n";
				if (type == 15 && channel != 15) {
					out += "      ";
					for (int x : data) {
						out += (char) x;
					}
				} else {
					for (int x : data) {
						out += "      " + x + "\n";
					}
				}
				return out;
			}
		}

		static int[] varLengthRead(FileInputStream f) throws IOException { // first int is result, second int is length
			int[] out = new int[2];
			int cur;
			int contin = 0;
			do {
				cur = f.read();
				contin = cur >> 7;
				cur &= 127;
				out[0] = out[0] << 7;
				out[0] |= cur;
				out[1]++;
			} while (contin == 1);
			return out;
		}
	}

	public static class InvalidChunk extends Chunk { //what do you think this is
		static InvalidChunk makeChunk(FileInputStream f) throws IOException {
			int[] temp = new int[4];
			for (int x = 0; x < 4; x++) {
				temp[x] = f.read();
			}
			int len = fromByteArray(temp);
			for (int x = 0; x < len; x++) {
				f.read();
			}
			return new InvalidChunk();
		}
	}

	static int fromByteArray(int[] bytes) {
		return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16) | ((bytes[2] & 0xFF) << 8)
				| ((bytes[3] & 0xFF) << 0);
	}
}