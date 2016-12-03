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

package io.github.mcolletta.mirchord.interpreter

import groovy.transform.CompileStatic

import io.github.mcolletta.mirchord.core.*
import static io.github.mcolletta.mirchord.core.Utils.*

import com.xenoage.utils.math.Fraction
import static com.xenoage.utils.math.Fraction.fr
import static com.xenoage.utils.math.Fraction._0

import com.googlecode.lingwah.Document
import com.googlecode.lingwah.ParseContext
import com.googlecode.lingwah.ParseResults
import com.googlecode.lingwah.StringDocument

import groovy.text.GStringTemplateEngine

@CompileStatic
class MirChordInterpreter {
	
	MirChordGrammar PARSER
	
	MirChordInterpreter() {
		PARSER = MirChordGrammar.INSTANCE
	}
	
	Score evaluate(String source) {
		Document doc= new StringDocument(source.trim())
		ParseContext ctx= new ParseContext(doc)
		ParseResults parseResults= ctx.getParseResults(PARSER.score, 0)
		if (!parseResults.success())
				throw parseResults.getError()
		MirChordProcessor processor = new MirChordProcessor([new MirchordAddon()])
		Score score = processor.process(parseResults)
		
		return score
	}
	
	public static void main(String[] args) {
		String source = new File("/home/mircoc/mirchords/test1.mirchord").text
		MirChordInterpreter interpreter = new MirChordInterpreter()
		Score score = interpreter.evaluate(source)
		println "RESULT: " + score
	}

}

@CompileStatic
class MirchordAddon {

	@MirChord 
	public Phrase transpose(int halfSteps, Phrase phrase) {
		Phrase newPhrase = phrase.copy()
		for(MusicElement el : newPhrase.elements) {
			if (el.getMusicElementType() == "Chord") {
				Chord chord = (Chord)el
				for(Pitch pitch : chord.getPitches()) {
					pitch.setMidiValue(pitch.getMidiValue() + halfSteps)
				}
			}
		}
		return newPhrase
	}

	@MirChord
	public Phrase transposeDiatonic(int diatonicSteps, String modeText, Phrase phrase) {
		KeyMode mode = ModeFromName(modeText)
		Phrase newPhrase = phrase.copy()
		for(MusicElement el : newPhrase.elements) {
			if (el.getMusicElementType() == "Chord") {
				Chord chord = (Chord)el
				int halfSteps = getHalfStepsFromDiatonic(chord.getPitch().symbol, diatonicSteps, mode)
				for(Pitch pitch : chord.getPitches()) {
					pitch.setMidiValue(pitch.getMidiValue() + halfSteps)
				}
			}
		}
		return newPhrase
	}

	@MirChord
	public Phrase invert(Phrase phrase) {
		Phrase newPhrase = phrase.copy()
		List<MusicElement> chords = newPhrase.elements.findAll { it.getMusicElementType() == "Chord" }
		Chord mirror = (Chord)chords[0]
		for(MusicElement el : newPhrase.elements) {
			if (el.getMusicElementType() == "Chord") {
				Chord chord = (Chord)el
				int interval = mirror.getPitch().getMidiValue() - chord.getPitch().getMidiValue()
				chord.getPitch().setMidiValue(mirror.getPitch().getMidiValue() + interval)
			}
		}
		return newPhrase
	}

	@MirChord
	public Phrase invertDiatonic(String modeText, Phrase phrase) {
		KeyMode mode = ModeFromName(modeText)
		Phrase newPhrase = phrase.copy()
		List<MusicElement> chords = newPhrase.elements.findAll { it.getMusicElementType() == "Chord" }
		Chord mirror = (Chord)chords[0]
		for(MusicElement el : newPhrase.elements) {
			if (el.getMusicElementType() == "Chord") {
				Chord chord = (Chord)el
				int interval = NOTE_NAMES[mirror.getPitch().symbol] - NOTE_NAMES[chord.getPitch().symbol]
				int halfSteps = getHalfStepsFromDiatonic(chord.getPitch().symbol, interval, mode)
				chord.getPitch().midiValue = mirror.getPitch().midiValue + halfSteps
			}
		}
		return newPhrase
	}

	@MirChord 
	public Phrase retrograde(Phrase phrase) {
		Phrase newPhrase = phrase.copy()
		newPhrase.elements = phrase.elements.reverse()
		return newPhrase
	}

	@MirChord 
	public Phrase augment(String ratio, Phrase phrase) {
		Fraction fraction = parseFraction(ratio)
		Phrase newPhrase = phrase.copy()
		for(MusicElement el : newPhrase.elements) {
			if (el.getMusicElementType() == "Chord") {
				Chord chord = (Chord)el
				chord.duration = chord.duration.mult(fraction)
			}
		}
		return newPhrase
	}

	@MirChord 
	public Phrase diminuition(String ratio, Phrase phrase) {
		Fraction fraction = parseFraction(ratio)
		Phrase newPhrase = phrase.copy()
		for(MusicElement el : newPhrase.elements) {
			if (el.getMusicElementType() == "Chord") {
				Chord chord = (Chord)el
				chord.duration = chord.duration.divideBy(fraction)
			}
		}
		return newPhrase
	}

	@MirChord 
	public Phrase chain(Phrase phrase, Phrase pattern) {
		Phrase newPhrase = phrase.copy()
		List<MusicElement> rhythm = pattern.elements.findAll { it.getMusicElementType() == "Chord" }

		int i = 0;
		for(MusicElement el : newPhrase.elements) {
			if (el.getMusicElementType() == "Chord") {
				Chord chord = (Chord)el
				chord.duration = (rhythm.size() < i) ? ((Chord)rhythm[i]).duration : ((Chord)rhythm[i % rhythm.size()]).duration
				i++
			}
		}
		return newPhrase
	}

}