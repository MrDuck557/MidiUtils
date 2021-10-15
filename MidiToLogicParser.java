package random;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
Turns a midi file into mlog instructions for MrDuck557's mindustry music player
Assumes that there are no chunks other than header or track
Made by MrDuck557
*/

public class MidiToLogicParser {

	public static void main(String[] args) throws IOException {
		MidiParser midi = new MidiParser(new File("")); // put input midi file here
		MidiParser.HeaderChunk head = (MidiParser.HeaderChunk) midi.nextChunk();
		FileWriter fileOut = new FileWriter(""); // put output text file here
		// use format to distinguish 0+1 from 2 during tempo changes
		MidiParser.TrackChunk[] trks = new MidiParser.TrackChunk[head.ntracks];
		for (int x = 0; x < head.ntracks; x++) {
			trks[x] = (MidiParser.TrackChunk) midi.nextChunk();
		}
		int timeFormat = head.tickdiv >> 15;
		int timeSplits = 0; // ticks per quarter note
		if (timeFormat == 0) {
			timeSplits = head.tickdiv & 32767;
		} // do timeFormat == 1 later
		MidiParser.TrackChunk.Event[] events = new MidiParser.TrackChunk.Event[head.ntracks];
		int[] lastTicks = new int[head.ntracks];
		for (int x = 0; x < head.ntracks; x++) {
			events[x] = trks[x].nextEvent();
		}
		// in case you want to ignore something
		boolean[] ignore = new boolean[head.ntracks];
		// manually set the values
		int tempo = 500000; // microseconds per quarter note
		// ticks to seconds: ticks / timeSplits * tempo / 1000000
		int minTime;
		int minIndex = 0;
		int lineCount = 0;
		StringBuilder out = new StringBuilder();
		int lastTime = 0; // last time a note was played, in ticks
		int index = 2; // index of next mlog command
		int temp;
		boolean[] done = new boolean[84];
		while (true) {
			// look for event at min tick
			minTime = Integer.MAX_VALUE;
			for (int x = 0; x < head.ntracks; x++) {
				if (events[x] != null && events[x].delta + lastTicks[x] < minTime) {
					minTime = events[x].delta + lastTicks[x];
					minIndex = x;
				}
			}
			// everything is null because out of events
			if (minTime == Integer.MAX_VALUE) {
				break;
			}
			// do stuff based on event type
			switch (events[minIndex].type) {
			case 9: // note on
				if (ignore[minIndex]) { // we want to ignore this track
					break;
				}
				temp = events[minIndex].data[0] - 24;
				if (temp < 27 || temp >= 84 || events[minIndex].data[1] == 0) { // B2 or lower and C8 and higher out of
																				// range
					break;
				}
				if ((1f * (minTime - lastTime) / timeSplits * tempo / 1000000) >= 0.05) {
					out.append("write -" + (1f * (minTime - lastTime) / timeSplits * tempo / 1000000) + " bank1 "
							+ index + "\n");
					lineCount++;
					index++;
					if (index >= 512) {
						index = 0;
						out.append("\n\nNew Proc\n\n\n");
					}
					lastTime = minTime;
					done = new boolean[84];
				}
				if (done[temp]) {
					break;
				}
				out.append("write " + temp + " bank1 " + index + "\n");
				lineCount++;
				done[temp] = true;
				index++;
				if (index >= 512) {
					index = 0;
					out.append("\n\nNew Proc\n\n");
				}
				break;
			case 15: // meta event
				if (events[minIndex].channel == 15 && events[minIndex].data[0] == 81) { // I only care about tempo
					tempo = events[minIndex].data[1] << 16 | events[minIndex].data[2] << 8 | events[minIndex].data[3];
				}
			}
			// update stuff
			lastTicks[minIndex] = minTime;
			events[minIndex] = trks[minIndex].nextEvent();
		}
		// header mlog
		out.insert(0, "write 1 bank1 0\nwrite " + lineCount + " bank1 1\n");
		fileOut.write(out.toString());
		fileOut.close();
		System.out.println("Total of " + lineCount + " lines, requires " + lineCount / 512.0 + "mem cells");
	}
}