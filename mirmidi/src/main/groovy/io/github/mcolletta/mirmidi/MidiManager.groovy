/*
 * Copyright (C) 2016 Mirco Colletta
 *
 * This file is part of MirComp.
 *
 * MirComp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MirComp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MirComp.  If not, see <http://www.gnu.org/licenses/>.
*/

/**
 * @author Mirco Colletta
 */

package io.github.mcolletta.mirmidi

import javax.sound.midi.*
import java.util.concurrent.ConcurrentSkipListMap

import javafx.beans.property.IntegerProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.LongProperty
import javafx.beans.property.SimpleLongProperty

import javafx.collections.transformation.SortedList

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.MapChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.event.EventHandler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import groovy.transform.TupleConstructor
import groovy.transform.ToString
import groovy.transform.CompileStatic

@CompileStatic
class MidiManager {

    Sequencer sequencer
    Sequence sequence
    Synthesizer synthesizer
    
    //long playbackPosition = 0L
    LongProperty playbackPosition = new SimpleLongProperty(0L)
    final long getPlaybackPosition() { return playbackPosition.get() }
    final void setPlaybackPosition(long value) { playbackPosition.set(value) }
    LongProperty playbackPositionProperty() { return playbackPosition }

    int timerRate = 50
    private volatile PlaybackThread  playbackThread
    private List<MidiPlaybackListener> listeners = []
    
    IntegerProperty resolution = new SimpleIntegerProperty(480)

    final int getResolution() { return resolution.get() }
    final void setResolution(int value) { resolution.set(value) }
    IntegerProperty resolutionProperty() { return resolution }

    Map<Integer,Boolean> usedChannels = [:].withDefault() { false }
    
    ObservableList<MidiNote> notes
    SortedList<MidiNote> sortedByEndNotes
    SortedList<MidiNote> sortedByDurationNotes  // longest duration in O(1)
    Map<Integer,Map<Integer,ObservableMap<Long,MidiCC>>> controllers

    long length = 0

    MidiEdit currentEdit
    int editIndex = -1
    List<MidiEdit> editHistory = []

    boolean isParsing

    int currentTrack = 0
    int currentChannel = 0
    int currentController = 7

    Map<Integer,MidiControllerInfo> controllersInfo = [:]

    Comparator sortByEndComparator = { MidiNote left, MidiNote right -> return (int)(left.getEnd() - right.getEnd())} as Comparator
    Comparator sortByDurationComparator = { MidiNote left, MidiNote right -> return (int)(left.getDuration() - right.getDuration())} as Comparator


    ListChangeListener<MidiNote> noteListener = { ListChangeListener.Change<? extends MidiNote> change ->
                if (!isParsing) {
                    while (change.next()) {
                        if (change.wasPermutated()) {
                            for (int i = change.getFrom(); i < change.getTo(); ++i) {
                                //println "permutate"
                            }
                        } else if (change.wasUpdated()) {
                            //println "update"
                        } else if (change.wasRemoved()) {
                            for (MidiNote note : change.getRemoved()) {
                                updateSequenceRemovedNote(note)
                                if (currentEdit) 
                                    currentEdit.noteRemoved.add(note)
                            }
                        } else if (change.wasAdded()) {
                            for (MidiNote note : change.getAddedSubList()) {
                                updateSequenceAddedNote(note)
                                if (currentEdit) 
                                    currentEdit.noteInserted.add(note)
                            }
                        }
                    }
                }
            } as ListChangeListener<MidiNote>

    MapChangeListener<Long, MidiCC> controllerListener = new MapChangeListener<Long, MidiCC>() {
                        @Override
                        public void onChanged(MapChangeListener.Change<? extends Long, ? extends MidiCC> change) {
                            if (!isParsing) {
                                if (change.wasAdded()) {
                                    updateSequenceAddedCC(change.getValueAdded())
                                    if (currentEdit) 
                                        currentEdit.ccInserted.add(change.getValueAdded())
                                }
                                if (change.wasRemoved()) {
                                    updateSequenceRemovedCC(change.getValueRemoved())
                                    if (currentEdit) 
                                        currentEdit.ccRemoved.add(change.getValueRemoved())
                                }
                            }
                        }
                    }


    MidiManager(){
        initMidi()
        loadSequence()
        // ctype: 0 curve, 1 on/off
        controllersInfo[7] = new MidiControllerInfo(info:"COARSE VOLUME", value:7, ctype: 0)
        controllersInfo[64] = new MidiControllerInfo(info:"SUSTAIN", value:64, ctype: 1)
    }

    // TODO get synthesizer from outside
    void initMidi() {
        try {
            sequencer =  MidiSystem.getSequencer()
            if (sequencer == null) {
                println("Sequencer not found from MidiSystem")
                System.exit(0)
            }
            sequencer.open()
            if (!(sequencer instanceof Synthesizer)) {
                synthesizer = MidiSystem.getSynthesizer()
                synthesizer.open()
                Receiver synthReceiver = synthesizer.getReceiver()
                Transmitter seqTransmitter = sequencer.getTransmitter()
                seqTransmitter.setReceiver(synthReceiver)
            } else {
                synthesizer = (Synthesizer)sequencer
            }
        } catch(MidiUnavailableException e) {
            println("No sequencer available")
            System.exit(0)
        } catch(Exception e) {
            e.printStackTrace()
        }
    }

    void loadMidi(String path) {
        File midiFile = new File(path)
        loadMidi(midiFile)
    }

    void loadMidi(File midiFile) {
        Sequence sequence = MidiSystem.getSequence(midiFile)
        loadSequence(sequence)
    }

    void loadSequence(Sequence sequence=null, int tracksCount=16) {
        try {
            if (sequence == null) {
                try {
                    sequence = new Sequence(Sequence.PPQ, getResolution())
                } catch (InvalidMidiDataException e) {
                    e.printStackTrace()
                }
                for (int i = 0; i < tracksCount; i++) {
                    sequence.createTrack()
                }
            }
            this.sequence = sequence
            sequencer.setSequence(sequence)
            parseEvents()
            setSequenceResolution()
        } catch(Exception e) {
            e.printStackTrace()
        }
    }

    void setSequenceResolution()
    {
        if (sequence != null) {
            setResolution(sequence.getResolution())
        }
    }

    void updateSequenceAddedNote(MidiNote note) {
        Track t = sequence.tracks[note.track]
        t.add(note.startEvent)
        t.add(note.endEvent)
    }

    void updateSequenceRemovedNote(MidiNote note) {
        Track t = sequence.tracks[note.track]
        t.remove(note.startEvent)
        t.remove(note.endEvent)
    }

    void updateSequenceAddedCC(MidiCC cc) {
        Track t = sequence.tracks[cc.track]
        t.add(cc.midiEvent)
    }

    void updateSequenceRemovedCC(MidiCC cc) {
        if (cc != null) {
            Track t = sequence.tracks[cc.track]
            t.remove(cc.midiEvent)
        }
    }

    MidiNote createMidiNote(int channel, int track, long start, long end, int pitch, int attachVelocity, int decayVelocity=0) {
        ShortMessage msg = new ShortMessage()
        msg.setMessage(ShortMessage.NOTE_ON, channel, pitch, attachVelocity)
        MidiEvent startEvt = new MidiEvent(msg, start)
        
        msg = new ShortMessage()
        if (decayVelocity > 0)
            msg.setMessage(ShortMessage.NOTE_OFF, channel, pitch, decayVelocity)
        else
            msg.setMessage(ShortMessage.NOTE_ON, channel, pitch, 0)
        MidiEvent endEvt = new MidiEvent(msg, end)

        MidiNote note = new MidiNote(startEvent:startEvt, endEvent:endEvt, track:track)
        return note
    }

    void addMidiNote(MidiNote note) {
        notes.add(note)
    }

    void removeMidiNote(MidiNote note) {
        notes.remove(note)
    }

    MidiCC createMidiCC(int channel, int track, long tick, int data1, int data2) {
        Track t = sequence.tracks[track]
        ShortMessage msg = new ShortMessage()
        //msg.setMessage(ShortMessage.CONTROL_CHANGE, channel, 7, val) // 7 is coarse volume
        int command = ShortMessage.CONTROL_CHANGE
        msg.setMessage(command, channel, data1, data2)
        MidiEvent midiEvt = new MidiEvent(msg, tick)
        MidiCC cc = new MidiCC(midiEvent:midiEvt, track:track)
        return cc
    }

    void addMidiCC(MidiCC cc) {
        this.controllers[cc.controller][cc.channel][cc.tick] = cc
    }

    void removeMidiCC(MidiCC cc) {
        this.controllers[cc.controller][cc.channel].remove(cc.tick)
    }

    long getLength() {
        if (sequence != null) {
            return sequence.getTickLength()
        }
        return 0
    }

    long getLongestDuration() {
        if (sortedByDurationNotes.size() > 0)
            return sortedByDurationNotes[sortedByDurationNotes.size()-1].getDuration()
        return 0
    }

    int getStartNoteIndex(long x) {
        // dummy event for search
        ShortMessage msg = new ShortMessage()
        msg.setMessage(ShortMessage.NOTE_OFF, 0, 0, 0)
        MidiEvent event = new MidiEvent(msg, x)
        MidiNote needle = new MidiNote(startEvent:event, endEvent:event, track:0)
        int idx = Collections.binarySearch(sortedByEndNotes, needle, sortByEndComparator)
        return idx
    }

    int getStartCCIndex(long x, ObservableMap<Long, MidiCC> om) {
        List<Long> keys = om.keySet() as List<Long>
        int idx = Collections.binarySearch(keys, x)
        return idx
    }

    void parseEvents() {
        isParsing = true

        notes = FXCollections.observableArrayList()
        notes.addListener(noteListener)
        sortedByEndNotes = new SortedList<>(notes)
        sortedByEndNotes.comparatorProperty().set(sortByEndComparator)
        sortedByDurationNotes = notes.sorted(sortByDurationComparator)

        // controllers[7][0][1200] = createMidiCC(<127>)  set the volume (CC#7) of channel 0 at tick 1200 to 127
        controllers = [:].withDefault { // controller, ex. 7 is volume
            [:].withDefault { // channel
                ObservableMap<Long, MidiCC> map = FXCollections.observableMap( new ConcurrentSkipListMap<Long, MidiCC>() )
                map.addListener(controllerListener)
                return map
            } 
        }

        Map<Integer,Map<Integer,MidiEvent>> cache = [:].withDefault() { [:] }
        for(int idx = 0; idx < sequence.getTracks().size(); idx++) {
            Track track = sequence.getTracks()[idx]
            for(int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i)
                long tick = event.getTick()
                //MidiMessage message = event.getMessage()
                if (event.getMessage() instanceof ShortMessage) {
                    ShortMessage message = event.getMessage() as ShortMessage
                    int channel = message.getChannel()
                    usedChannels[channel] = true
                    switch (message.getCommand()) {
                        case ShortMessage.CONTROL_CHANGE:       // 0xB0, 176
                            MidiCC cc = new MidiCC(midiEvent:event, track:idx)
                            controllers[cc.controller][cc.channel][cc.tick] = cc
                            break
                        case ShortMessage.NOTE_ON:              // 0x90, 144
                            if (message.getData2() == 0) {
                                // A velocity of zero in a note-on event is a note-off event
                                MidiEvent evt = cache[channel][message.getData1()]
                                MidiNote note = new MidiNote(startEvent:evt, endEvent:event, track:idx)
                                notes.add(note)
                            } else {
                                cache[channel][message.getData1()] = event
                            }
                            break;
                        case ShortMessage.NOTE_OFF:    // 0x80, 128
                            MidiEvent evt = cache[channel][message.getData1()]
                            if (evt != null) {
                                MidiNote note = new MidiNote(startEvent:evt, endEvent:event, track:idx)
                                notes.add(note)
                            } else {
                                throw new Exception("NOTE_OFF event without NOTE_ON during the parsing of midi file")
                            }
                            break;
                        default : 
                            //println "Unparsed message: " + message.command
                            break
                    }
                }

            }
        }
        
        isParsing = false
    }

    // SAVE

    void saveAs(File selectedFile) {
        int type = getPreferredMidiType(sequence)
        MidiSystem.write(sequencer.getSequence(), type, selectedFile)
    }

    static int getPreferredMidiType(Sequence sequence) {
        int[] types = MidiSystem.getMidiFileTypes(sequence)
        int type = 0;
        if (types.length != 0) {
            type = types[types.length - 1]
        }
        return type;
    }

    // PLAYBACK

    void registerListener(MidiPlaybackListener listener) {
        listeners.add(listener)
    }

    void play() {
        if (sequencer.getSequence() != null) {
            sequencer.start()
            // println "sequence started"
            if (playbackThread != null)
                playbackThread.stopPlayback()
            playbackThread = new PlaybackThread()
            playbackThread.start()
            for (MidiPlaybackListener listener : listeners) {
                listener.playbackStarted()
            }
        }
    }

    void pause() {
        if (sequencer.isRunning()) {
            sequencer.stop()
            if (playbackThread != null)
                playbackThread.stopPlayback()
            for (MidiPlaybackListener listener : listeners) {
                listener.playbackPaused()
            }
        }
    }

    void stop() {
        if (sequencer.isRunning()) {
            sequencer.stop()
            if (playbackThread != null)
                playbackThread.stopPlayback()
        }
        setPlaybackPosition(0L)
        for (MidiPlaybackListener listener : listeners) {
            listener.playbackStopped()
        }
    }

    private class PlaybackThread extends Thread {

        private boolean stop = false
        
        public PlaybackThread() {}

        @Override public void run() {
            try {
                sequencer.setTickPosition(getPlaybackPosition())
                while (!stop) {
                    long tick = sequencer.getTickPosition()
                    for (MidiPlaybackListener listener : listeners) {
                        listener.playbackAtTick(tick)
                    }
                    Thread.sleep(1000 / timerRate)
                }
            } catch (InterruptedException e) {
            }
        }

        public void stopPlayback() {
            stop = true
        }
    }


    // UNDOABLE

    private class MidiEdit {

        List<MidiNote> noteInserted = []
        List<MidiNote> noteRemoved = []
        List<MidiCC> ccInserted = []
        List<MidiCC> ccRemoved = []

        void undo() {
            // ORDER IS IMPORTANT: reverse respect to the action
            for(MidiNote note : noteInserted) {
                removeMidiNote(note)
            }
            for(MidiNote note : noteRemoved) {
                addMidiNote(note)
            }
            for(MidiCC cc : ccInserted) {
                removeMidiCC(cc)
            }
            for(MidiCC cc : ccRemoved) {
                addMidiCC(cc)
            }
        }

        void redo() {
            for(MidiNote note : noteRemoved) {
                removeMidiNote(note)
            }
            for(MidiNote note : noteInserted) {
                addMidiNote(note)
            }
            for(MidiCC cc : ccRemoved) {
                removeMidiCC(cc)
            }
            for(MidiCC cc : ccInserted) {
                addMidiCC(cc)
            }
        }
    }

    void undo() {
        if (editHistory.size() > 0 && editIndex > -1) {
            editHistory[editIndex].undo()
            editIndex -= 1
        }
    }

    boolean hasUndo() {
        return (editHistory.size() > 0 && editIndex > -1)
    }

    void redo() {
        if (editIndex < editHistory.size()-1) {
            editIndex += 1
            editHistory[editIndex].redo()
        }
    }

    boolean hasRedo() {
        return (editHistory.size() > 0 && editIndex < (editHistory.size()-1))
    }

    void startEdit() {
        currentEdit = new MidiEdit()
        if (editHistory.size() > 0 && editIndex < (editHistory.size()-1))
            editHistory = editHistory - editHistory[editIndex+1..editHistory.size()-1]
    }

    void stopEdit() {
        editHistory.add(currentEdit)
        editIndex += 1
        currentEdit = null
    }
}


@CompileStatic
//@ToString(includeNames=true, includeFields=true, excludes='startEvent,endEvent')
@TupleConstructor // in addition to default name-arg constructor
class MidiNote {

    MidiEvent startEvent 
    MidiEvent endEvent   

    int track
    int pitch
    int attachVelocity
    int decayVelocity

    long getStart() {
        return startEvent.getTick()
    }

    long getEnd() {
        return endEvent.getTick()
    }

    int getPitch() {
        ShortMessage message = startEvent.getMessage() as ShortMessage
        int pitch = message.getData1()
        return pitch
    }

    int getVelocity() {
        ShortMessage message = startEvent.getMessage() as ShortMessage
        int velocity = message.getData2()
        return velocity
    }

    int getChannel() {
        ShortMessage message = startEvent.getMessage() as ShortMessage
        int channel = message.getChannel()
        return channel
    }

    long getDuration() {
        return end - start
    }

    String toString() {
        return "pitch:" + getPitch() + "   start:" + getStart() + "   end:" + getEnd()
    }

}

@CompileStatic
@ToString(includeNames=true, includeFields=true, excludes='midiEvent')
@TupleConstructor
class MidiData {

    MidiEvent midiEvent

    int getChannel() {
        ShortMessage message = midiEvent.getMessage() as ShortMessage
        int channel = message.getChannel()
        return channel
    }

    long getTick() {
        return midiEvent.getTick()
    }

    int track
}

class MidiCC extends MidiData {


    int getController() {
        ShortMessage message = midiEvent.getMessage() as ShortMessage
        int data1 = message.getData1()
        return data1
    }

    int getValue() {
        ShortMessage message = midiEvent.getMessage() as ShortMessage
        int data2 = message.getData2()
        return data2
    }

    String toString() {
        return "CC#" + getController() + "=" + getValue() + "[" + getTick() + "]"
    }

}

@TupleConstructor
class MidiControllerInfo {
    String info
    int value
    int ctype

    String toString() {
        return info
    }
}

interface MidiPlaybackListener {

    static final int timerRate = 50

    void playbackAtTick(long tick)

    void playbackStarted()

    void playbackPaused()

    void playbackStopped()

    void playbackAtEnd()

}