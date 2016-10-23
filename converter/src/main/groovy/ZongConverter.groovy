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

package io.github.mcolletta.mircomp.converter

import com.xenoage.zong.io.midi.out.MidiTools

import static io.github.mcolletta.mircomp.utils.Utils.*

import io.github.mcolletta.mircomp.mirchord.Phrase
import io.github.mcolletta.mircomp.mirchord.Anchor
import io.github.mcolletta.mircomp.mirchord.Repeat
// http://groovy-lang.org/structure.html#_import_aliasing
import io.github.mcolletta.mircomp.mirchord.Score as MirScore
import io.github.mcolletta.mircomp.mirchord.Rest as MirRest
import io.github.mcolletta.mircomp.mirchord.Chord as MirChord
import io.github.mcolletta.mircomp.mirchord.Part as MirPart
import io.github.mcolletta.mircomp.mirchord.Voice as MirVoice
import io.github.mcolletta.mircomp.mirchord.Instrument as MirInstrument
import io.github.mcolletta.mircomp.mirchord.Key as MirKey
import io.github.mcolletta.mircomp.mirchord.Time as MirTime
import io.github.mcolletta.mircomp.mirchord.Tempo as MirTempo
import io.github.mcolletta.mircomp.mirchord.ClefType as MirClefType
import io.github.mcolletta.mircomp.mirchord.Clef as MirClef
import io.github.mcolletta.mircomp.mirchord.Tuplet as MirTuplet
import io.github.mcolletta.mircomp.mirchord.StemDirection as MirStemDirection

import com.xenoage.zong.core.Score
import com.xenoage.zong.core.position.MP
import com.xenoage.zong.io.selection.Cursor
import com.xenoage.zong.core.format.StaffLayout
import com.xenoage.zong.core.instrument.Instrument
import com.xenoage.zong.core.instrument.PitchedInstrument
import com.xenoage.zong.core.instrument.UnpitchedInstrument

import com.xenoage.zong.core.music.ColumnElement
import com.xenoage.zong.core.music.Measure
import com.xenoage.zong.core.music.MeasureElement
import com.xenoage.zong.core.music.Part
import com.xenoage.zong.core.music.Staff
import com.xenoage.zong.core.music.clef.Clef
import com.xenoage.zong.core.music.format.Position
import com.xenoage.zong.core.music.key.TraditionalKey
import com.xenoage.zong.core.music.key.TraditionalKey.Mode
import com.xenoage.zong.core.music.time.Time
import com.xenoage.zong.core.music.time.TimeType
import com.xenoage.zong.core.music.rest.Rest
import com.xenoage.zong.core.music.slur.Slur
import com.xenoage.zong.core.music.slur.SlurType
import com.xenoage.zong.core.music.slur.SlurWaypoint
import com.xenoage.zong.core.music.beam.Beam
import com.xenoage.zong.core.music.beam.BeamWaypoint

import com.xenoage.zong.commands.core.music.PartAdd
import com.xenoage.zong.commands.core.music.MeasureAdd
import com.xenoage.zong.commands.core.music.ColumnElementWrite
import com.xenoage.zong.commands.core.music.slur.SlurAdd

import static com.xenoage.utils.collections.CollectionUtils.addOrNew;
import static com.xenoage.utils.collections.CollectionUtils.alist;
import static com.xenoage.utils.math.Fraction._0;
import static com.xenoage.utils.math.Fraction.fr;
import static com.xenoage.zong.core.music.Pitch.A;
import static com.xenoage.zong.core.music.Pitch.B;
import static com.xenoage.zong.core.music.Pitch.C;
import static com.xenoage.zong.core.music.Pitch.D;
import static com.xenoage.zong.core.music.Pitch.E;
import static com.xenoage.zong.core.music.Pitch.F;
import static com.xenoage.zong.core.music.Pitch.G;
import static com.xenoage.zong.core.music.Pitch.pi;
import static com.xenoage.zong.core.music.format.SP.sp;
import static com.xenoage.zong.core.position.MP.mp;
import static com.xenoage.zong.core.text.UnformattedText.ut;

import com.xenoage.zong.commands.core.music.StaffAdd
import com.xenoage.zong.commands.core.music.VoiceAdd
import com.xenoage.zong.commands.core.music.VoiceAdd
import com.xenoage.zong.commands.core.music.VoiceElementWrite
import com.xenoage.zong.commands.core.music.MeasureElementWrite;
import com.xenoage.zong.core.music.InstrumentChange;
import com.xenoage.zong.core.music.clef.Clef;
import com.xenoage.zong.core.music.clef.ClefType;
import com.xenoage.zong.core.music.Voice
import com.xenoage.zong.core.music.VoiceElement
import com.xenoage.zong.core.music.rest.Rest
import com.xenoage.zong.core.music.chord.Chord
import com.xenoage.zong.core.music.chord.Note
import com.xenoage.zong.core.music.Pitch
import com.xenoage.zong.core.music.tuplet.Tuplet
import com.xenoage.zong.core.music.barline.BarlineStyle
import com.xenoage.zong.core.music.StavesList
import com.xenoage.zong.core.music.direction.Tempo

import com.xenoage.zong.core.music.chord.Stem
import com.xenoage.zong.core.music.chord.StemDirection

import static com.xenoage.zong.core.music.beam.Beam.beam
import static com.xenoage.zong.core.music.util.DurationInfo.getDots
import static com.xenoage.zong.core.music.barline.Barline.barlineBackwardRepeat
import static com.xenoage.zong.core.music.barline.Barline.barlineForwardRepeat
import static com.xenoage.zong.core.music.time.TimeType.timeType
//import static com.xenoage.zong.core.music.chord.ChordFactory.chord
import static com.xenoage.zong.core.position.MP.atBeat

import com.xenoage.zong.utils.exceptions.MeasureFullException

import com.xenoage.utils.math.Fraction


class ZongConverter {

	Score score
	int currentStaff
	int currentMeasure
	String currentVoice

	def context
	def slurMap
	def anchors
	def instrumentsDefault
	def currentInstrument

	ArrayList<BeamWaypoint> openBeamWaypoints

	ZongConverter() {}

	Score convert(MirScore mirscore) {

		score = new Score()
		// set default score format
		float _is = score.getFormat().getInterlineSpace()
		StaffLayout staffLayout = new StaffLayout((float)(_is * 9))
		score.getFormat().setStaffLayoutOther(staffLayout)

		slurMap = [:]
		instrumentsDefault = [:].withDefault { true }
		currentInstrument = [:].withDefault { [:] }
		// anchors[staff][id] one anchor per part
		anchors = [:].withDefault { [:] }
		// context[staff][measure][voice]
		context = [:].withDefault { [:].withDefault { [:] } }

		mirscore.parts.each { idp, part ->
			currentStaff = score.getStavesCount()
			addPart(idp, part)
			part.voices.each { idv, voice ->
				openBeamWaypoints == null
				currentMeasure = 0
				currentVoice = idv
				VoiceContext ctx = createVoiceContext( mp(currentStaff, 0, MP.unknown, _0, 0) )
				for(el in voice.elements) {
					addElement(el)
				}
				closeBeam() // be sure every beam closed
			}
		}
		return score
	}

	void addPart(String idPart, MirPart mirpart) {
		Instrument defaultInstr = Instrument.defaultInstrument
		Part zpart = new Part(idPart, null, 1, alist(defaultInstr))
		new PartAdd(score, zpart, currentStaff, null).execute()
	}

	void addElement(el) {
		switch (el) {
			case { it instanceof MirRest}:
				addRest(el)
				break
			case { it instanceof MirChord}:
				addChord(el)
				break
			case { it instanceof MirClef }:
				addClef(el)
				break
			case { it instanceof MirKey }:
				addKey(el)
				break
			case { it instanceof MirTime }:
				addTime(el)
				break
			case { it instanceof MirTempo }:
				addTempo(el)
				break
			case { it instanceof Phrase }:
				addPhrase(el)
				break
			case { it instanceof Repeat }:
				addRepeat(el)
				break
			case { it instanceof Anchor }:
				addAnchor(el)
				break
			case { it instanceof MirTuplet }:
				addTuplet(el)
				break
			case { it instanceof MirInstrument }:
				addInstrument(el)
				break
			default:
				break
		}
	}

	void addAnchor(Anchor anchor) {
		if (anchors[currentStaff].containsKey(anchor.id)) { // use anchor
			MP mpos = anchors[currentStaff][anchor.id]
			currentMeasure = mpos.measure
			VoiceContext ctx = createVoiceContext( mp(mpos.staff, mpos.measure, MP.unknown, mpos.beat, MP.unknown) )
		} else { // create a new MP for anchor
			VoiceContext ctx = getContext()
			MP mpos = ctx.mp
			anchors[currentStaff][anchor.id] = mp(mpos.staff, mpos.measure, MP.unknown, mpos.beat, MP.unknown)
		}
	}

	// COLUMN ELEMENTS -------------------------------------------------------------------------
	// barline is a column element so for all staves
	void addRepeat(Repeat repeat) {		
		VoiceContext ctx = getContext()
		Fraction remain = ctx.getRemain()
		if (repeat.start) {
			ColumnElement barline = barlineForwardRepeat(BarlineStyle.HeavyLight)
			if (remain == 0)
				score.getColumnHeader(mp.measure).setStartBarline(barline)
			else
				write(barline)
		}	
		if (repeat.stop) {
			ColumnElement barline = barlineBackwardRepeat(BarlineStyle.HeavyLight, repeat.times)
			if (remain == 0)
				score.getColumnHeader(mp.measure).setEndBarline(barline)
			else
				write(barline)
		}
	}

	void addKey(MirKey mirkey) {
		// TODO mode
		//cursor.write((ColumnElement) new TraditionalKey(mirkey.fifths, Mode.Minor));
		write((ColumnElement) new TraditionalKey(mirkey.fifths))
	}

	void addTime(MirTime mirtime) {
		Time ztime = null
		switch (mirtime.time) {
			case { it == null}:
				ztime = new Time(TimeType.timeCommon)
				break
			case { it == f1}:
				ztime = new Time(TimeType.timeCommon)
				break
			case { it == f2}:
				ztime = new Time(TimeType.time_2_4)
				break
			case { it == f3}:
				ztime = new Time(TimeType.time_6_8)
				break
			default:
				ztime = new Time(timeType(mirtime.time.numerator, mirtime.time.denominator))
				break
		}
		write(ztime)
		updateContext()
	}

	void addTempo(MirTempo mirtempo) {
		Tempo tempo = new Tempo(mirtempo.baseBeat, mirtempo.beatsPerMinute)
		if (mirtempo.text != null)
			tempo.setText(ut(mirtempo.text))
		write((ColumnElement) tempo)
	}

	// ----------------------------------------------------------------------------------------

	void addInstrument(MirInstrument mirinstrument) {
		VoiceContext ctx = getContext()
		MP mpos = ctx.mp
		Instrument instrument
		if (mirinstrument.unpitched)
			instrument = new UnpitchedInstrument(mirinstrument.id)
		else
			instrument = new PitchedInstrument(mirinstrument.id)
		instrument.setMidiProgram(mirinstrument.midiProgram)
		StavesList stavesList = score.getStavesList() //stavesList.getParts()
		Part part = stavesList.getPartByStaffIndex(currentStaff)		
		if (instrumentsDefault[currentStaff] && mpos.measure < 1) {
			part.instruments = [instrument]
			instrumentsDefault[currentStaff] = false
		} else {
			part.instruments.add(instrument)
			new MeasureElementWrite(new InstrumentChange(instrument), score.getMeasure(mpos), mpos.beat).execute()
		}
		// one instrument per voice - UnPitchedInstrument
		currentInstrument[currentStaff][currentVoice] = instrument
	}

	void addClef(MirClef mirclef) {
		ClefType cleftype
		switch (mirclef.type) {
			case { it == MirClefType.Treble}:
				cleftype = ClefType.clefTreble
				break
			case { it == MirClefType.Bass}:
				cleftype = ClefType.clefBass
				break
			case { it == MirClefType.Percussion}:
				cleftype = ClefType.clefPercTwoRects
				break
			default:
				cleftype = ClefType.clefTreble
				break
		}
		Clef clef = new Clef(cleftype)
		write(clef)
	}

	void addTuplet(MirTuplet mirtuplet) {
		Fraction fraction = mirtuplet.fraction		
		def tuplet_chords = []
		mirtuplet.elements.each { el -> 
			def ratio = fr(mirtuplet.fraction.denominator, mirtuplet.fraction.numerator)
			def actualDuration = el.duration.mult(ratio)
			el.duration = actualDuration
			addChord(el, mirtuplet, tuplet_chords) 
		}
		Fraction base_duration = mirtuplet.getBaseDuration()
		Tuplet tuplet = new Tuplet(fraction.numerator, fraction.denominator, base_duration, true, tuplet_chords)
		tuplet_chords.each { Chord c ->
			c.tuplet = tuplet
		}
	}

	void addPhrase(Phrase phrase) {
		phrase.elements.each { el -> addChord(el) }
	}

	void addRest(MirRest mirrest) {
		VoiceContext ctx = getContext()
		Fraction remain = ctx.getRemain()
		Fraction actualDuration = mirrest.duration

		if (remain == _0 || remain >= actualDuration) {
			Rest zrest
			List<Fraction> durations = [actualDuration]
			if (isPowerOfTwo(actualDuration.denominator))
				durations = splitFraction(actualDuration)
			durations.each { zduration ->
				zrest = new Rest(zduration)
				if (mirrest.hidden)
					zrest.setHidden(true)
				write(zrest)
			}		
		}
		else {		
			Fraction new_remain = actualDuration.sub(remain)
			MirRest new_mirrest1 = [duration: remain]
			MirRest new_mirrestd2 = [duration: new_remain]
			addElement(new_mirrest1)
			addElement(new_mirrestd2)
		}
	}

	void addChord(MirChord mirchord, MirTuplet mirtuplet = null, List<Chord> tuplet_chords = null) {
		VoiceContext ctx = getContext()
		Fraction remain = ctx.getRemain()
		Fraction msize = ctx.measureSize

		Fraction ratio = null
		Fraction actualDuration = mirchord.duration

		if ((remain == _0 && actualDuration <= msize) || remain >= actualDuration) {			
			def zpitches = []
			mirchord.midiPitches.each { pitch ->
				zpitches <<	((Pitch)MidiTools.getPitchFromNoteNumber(pitch))
			}
			List<Fraction> durations = [actualDuration]
			if (mirtuplet == null && isPowerOfTwo(actualDuration.denominator)) // split only if chord not part of a tuplet
				durations = splitFraction(actualDuration)
			Chord zchord
			Chord lastChord = null
			int last = durations.size()-1
			durations.eachWithIndex { zduration, i ->
				// use spread operator to sort pitches asc as required by Zong
				zchord = chord(zduration, mirchord.unpitched, mirchord.stem, *(zpitches.toSorted()))
				write(zchord)
				// chord part of tuplet
				if (mirtuplet != null && tuplet_chords != null) {
					tuplet_chords << zchord
				}
				// manage ties				
				// two measures ties
				if(i == last && mirchord.tieStart) {  // last element
					slurMap['start'] = slurwp(zchord)
				}
				if(i == 0 && mirchord.tieEnd && slurMap['start'] != null) {  // first element
					slur(slurMap['start'],  slurwp(zchord))
					slurMap = [:]
				}
				// same measure ties
				if (durations.size() > 1 && i != 0) // not first element -> tieEnd and possibly tieStart if not last
					slur(slurwp(lastChord), slurwp(zchord))
				lastChord = zchord
			}	
		}
		else {
			Fraction d1 = remain
			if (remain == _0)
				d1 = msize
			Fraction d2 = actualDuration.sub(d1)
			//MirChord new_mirchord1 = mirchord.copyWith(duration: remain, tieStart: true)
			//MirChord new_mirchord2 = mirchord.copyWith(duration: new_remain, tieEnd: true)
			MirChord new_mirchord1 = [midiPitches:mirchord.midiPitches, duration:d1, stem:mirchord.stem, tieStart:true]
			MirChord new_mirchord2 = [midiPitches:mirchord.midiPitches, duration:d2, stem:mirchord.stem, tieEnd:true]
			addChord(new_mirchord1, mirtuplet, tuplet_chords)
			addChord(new_mirchord2, mirtuplet, tuplet_chords)
		}
		
	}

	static boolean checkFractionList(list, fr) {
		Fraction sum = _0
		list.each {
			sum = sum.add(it)
		}
		return (sum == fr)
	}

	static boolean isFractionAllowed(Fraction fr) {
		if (fr in [f1, f2, f4, f8, f16, f32, f64, f128, f256]) 
			return true
		if (getDots(fr) > 0)
			return true
		return false
	}

	static List<Fraction> splitFraction(Fraction fr, boolean checkList=false) {
		if (fr == _0)
			throw new Exception("splitFraction: try to split a zero duration")
		List<Fraction> list = []
		splitFractionRecur(fr, list)
		if (checkList && !(checkFractionList(list,fr))) {
			println "Problem splitting $fr with $list"
			return [fr]
		}
		return list
	}

	static void splitFractionRecur(Fraction fr, List<Fraction> list) {
		if (isFractionAllowed(fr)) {
			list << fr
		} else {
			switch (fr) {
				case { it > f2dotdot }:
					Fraction new_fr = fr.sub(f2dotdot)
					list << f2dotdot
					splitFractionRecur(new_fr, list)
					break
				case { it > f3 }:  // f2dot
					Fraction new_fr = fr.sub(f3)
					list << f3
					splitFractionRecur(new_fr, list)
					break
				case { it > f2 }:
					Fraction new_fr = fr.sub(f2)
					list << f2
					splitFractionRecur(new_fr, list)
					break
				case { it > f4dot }:
					Fraction new_fr = fr.sub(f4dot)
					list << f4dot
					splitFractionRecur(new_fr, list)
					break
				case { it > f4 }:
					Fraction new_fr = fr.sub(f4)
					list << f4
					splitFractionRecur(new_fr, list)
					break
				case { it > f8 }:
					Fraction new_fr = fr.sub(f8)
					list << f8
					splitFractionRecur(new_fr, list)
					break
				case { it > f16 }:
					Fraction new_fr = fr.sub(f16)
					list << f16
					splitFractionRecur(new_fr, list)
					break
				case { it > f32 }:
					Fraction new_fr = fr.sub(f32)
					list << f32
					splitFractionRecur(new_fr, list)
					break
				case { it > f64 }:
					Fraction new_fr = fr.sub(f64)
					list << f64
					splitFractionRecur(new_fr, list)
					break
				case { it > f128 }:
					Fraction new_fr = fr.sub(f128)
					list << f128
					splitFractionRecur(new_fr, list)
					break
				case { it > f256 }:
					Fraction new_fr = fr.sub(f256)
					list << f256
					splitFractionRecur(new_fr, list)
					break
				default:
					//println "Value ${fr} too small: return it as is"
					break
			}
		}
	}

	static void slur(SlurWaypoint start, SlurWaypoint stop, SlurType slurType=SlurType.Tie) {
		new SlurAdd(new Slur(slurType, start, stop, null)).execute()
	}

	static SlurWaypoint slurwp(Chord c) {
		return new SlurWaypoint(c, null, null)
	}


	Chord chord(Fraction fraction, boolean unpitched, MirStemDirection mirStemDir, Pitch... pitches) {
        Chord chord
        if (unpitched) {
        	UnpitchedInstrument instr = currentInstrument[currentStaff][currentVoice]
        	List<Note> notes = []
        	pitches.each { pitch -> 
        		Note note = new Note(pitch)
        		note.unpitched = true
        		note.instrument = instr
        		notes << note
        	}
        	chord = new Chord(notes, fraction)
        } else {
        	chord = new Chord(Note.notes(pitches), fraction)
        }

        if (mirStemDir != null) {
			StemDirection stemDir = StemDirection.Default
			switch (mirStemDir) {
				case { it == MirStemDirection.Up}:
					stemDir = StemDirection.Up
					break
				case { it == MirStemDirection.Down}:
					stemDir = StemDirection.Down
					break
				case { it == MirStemDirection.Auto}:
					stemDir = StemDirection.Default
					break
				default:
					break
			}
			chord.setStem( new Stem(stemDir, null) )
		}

        return chord
    }

    // ------------------------------------------------------------------------------



    VoiceContext getContext() {
    	return context[currentStaff][currentMeasure][currentVoice]
    }

    VoiceContext createVoiceContext(MP mpos) {
    	Staff staff = score.getStaff(mpos.staff)
		if (score.getMeasuresCount() <= mpos.measure) 
			new MeasureAdd(score, mpos.measure - score.getMeasuresCount() + 1).execute()
		Measure measure = staff.getMeasure(mpos.measure)
		if (mpos.measure == 0) {
			new ColumnElementWrite(new Time(TimeType.time_4_4), score.getColumnHeader(mpos.measure), _0, null).execute()
	        new ColumnElementWrite(new TraditionalKey(0), score.getColumnHeader(mpos.measure), _0, null).execute()
		}
		// allocate new voice for measure
		int voiceIndex = measure.getVoices().size()
		MP new_mpos = mp(mpos.staff, mpos.measure, voiceIndex, mpos.beat, mpos.element)
		Time time = score.getHeader().getTimeAtOrBefore(mpos.getMeasure())
		Fraction measureDuration = time.getType().getMeasureBeats()
		if (measureDuration == null) { // no time given for measure, put a common time
			time = new Time(TimeType.timeCommon)
			measureDuration = time.getType().getMeasureBeats()
		}
		// automatic beaming
		def pulses = getPulses(time)
		// --------------------
		VoiceContext voiceContext = [measureSize: measureDuration, mp: new_mpos, pulses:pulses]
		context[mpos.staff][mpos.measure][currentVoice] = voiceContext
		return voiceContext
    }

    void updateContext() {
		Time time = score.getHeader().getTimeAtOrBefore(currentMeasure)
		Fraction measureDuration = time.getType().getMeasureBeats()
		if (measureDuration == null) { // no time given for measure, put a common time
			measureDuration = new Time(TimeType.timeCommon).getType().getMeasureBeats()
		}
		context[currentStaff][currentMeasure].each { String idv, VoiceContext ctx ->
			ctx['measureSize'] = measureDuration
		}
	}

    // from Zong Cursor -------------------------------------------------------------

    void write(VoiceElement element)
		throws MeasureFullException {

		VoiceContext ctx = getContext()
		if (ctx.getRemain() == _0) { // measure with voice full: create a new measure
			currentMeasure += 1
			ctx = createVoiceContext( mp(currentStaff, currentMeasure, MP.unknown, _0, 0) )
			closeBeam()
		}
		MP mpos = ctx.mp
		Fraction duration = element.getDuration()

		ensureVoiceExists(mpos)

		VoiceElementWrite.Options options = new VoiceElementWrite.Options()
		options.checkTimeSignature = true
		options.fillWithHiddenRests = false

		Voice voice = score.getVoice(mpos)		
		new VoiceElementWrite(voice, mpos, element, options).execute()

		Fraction newBeat = mpos.beat.add(duration)
		int elementIndex = mpos.element
		if (elementIndex == MP.unknown)
			elementIndex = voice.getElementIndex(newBeat)
		else
			elementIndex += 1
		ctx.mp = mp(mpos.staff, mpos.measure, mpos.voice, newBeat, elementIndex)

		// automatic beaming
		if (element instanceof Chord) {
			Chord chord = (Chord) element
			def pulse = getChordPulse(mpos.beat, newBeat, ctx.pulses)
			// println "ctx.lastPulse=${ctx.lastPulse}        ChordPulse for $chord = $pulse"
			if (pulse != ctx.lastPulse) 
				closeBeam()				
			if (duration <= f8) // beam candidate
				openBeam(chord)
			else 
				closeBeam()
			ctx.lastPulse = pulse
		} else
			closeBeam()  // a rest		
		//------------------
	}

	void write(MeasureElement element) {
		VoiceContext ctx = getContext()
		MP mpos = atBeat(ctx.mp.staff, ctx.mp.measure, -1, ctx.mp.beat)
		ensureMeasureExists(mpos)
		new MeasureElementWrite(element, score.getMeasure(mpos), mpos.beat).execute()
	}

	void write(ColumnElement element) {
		VoiceContext ctx = getContext()
		MP mpos = atBeat(ctx.mp.staff, ctx.mp.measure, -1, ctx.mp.beat)
		ensureMeasureExists(mpos)
		new ColumnElementWrite(element, score.getColumnHeader(mpos.measure), mpos.beat, null).execute()
	}

    private void ensureStaffExists(MP mp) {
		if (score.getStavesCount() <= mp.staff)
			new StaffAdd(score, mp.staff - score.getStavesCount() + 1).execute()
	}
	
	private void ensureMeasureExists(MP mp) {
		ensureStaffExists(mp)
		if (score.getMeasuresCount() <= mp.measure)
			new MeasureAdd(score, mp.measure - score.getMeasuresCount() + 1).execute()
	}
	
	private void ensureVoiceExists(MP mp) {
		ensureMeasureExists(mp)
		Measure measure = score.getMeasure(mp)
		if (measure.getVoices().size() <= mp.voice)
			new VoiceAdd(measure, mp.voice).execute()
	}

	void openBeam(chord) {
		if (openBeamWaypoints == null)
			openBeamWaypoints = new ArrayList<BeamWaypoint>()
		openBeamWaypoints.add(new BeamWaypoint(chord, false))
	}

	void closeBeam() {
		if (openBeamWaypoints != null && openBeamWaypoints.size() > 1) {
			Beam beam = beam(openBeamWaypoints)
			for (BeamWaypoint wp : openBeamWaypoints)
				wp.getChord().setBeam(beam)
		}
		openBeamWaypoints = null
	}

	// -------------------------------------------------------------------------------

	private class VoiceContext {
		Fraction measureSize		
		MP mp
		def pulses
		def lastPulse = 1

		def getCurrentPulse() {
			return getBeatPulse(mp.beat, pulses)
		}

		Fraction getRemain() {
			return (measureSize.sub(mp.beat))
		}
	}
}