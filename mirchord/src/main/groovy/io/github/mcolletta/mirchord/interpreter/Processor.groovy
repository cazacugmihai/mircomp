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

import io.github.mcolletta.mirchord.core.*
import static io.github.mcolletta.mirchord.core.Utils.*

import groovy.transform.CompileStatic

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer

import com.xenoage.utils.math.Fraction
import static com.xenoage.utils.math.Fraction.fr
import static com.xenoage.utils.math.Fraction._0

import com.googlecode.lingwah.*
import com.googlecode.lingwah.util.*
import com.googlecode.lingwah.annotations.Processes

import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.lang.reflect.Modifier

import java.lang.annotation.Target
import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Target([ElementType.METHOD])
@Retention(RetentionPolicy.RUNTIME)
@interface MirChord {
    String info() default "MirChord method used by the interpreter"
}

@CompileStatic
@Processes(MirChordGrammar.class)
class MirChordProcessor extends AbstractProcessor {

	Score score
	String currentPart
	Map<String, String> currentVoice = [:]

    Stack environments //scopes
    Map<String, Integer> pitchMap = [C:0, D:2, E:4, F:5, G:7, A:9, B:11]
	Map<String, Integer> NOTE_NAMES = [C: 1, D: 2, E: 3, F: 4, G: 5, A: 6, B: 7]
	
	def modes = [
		0: [2, 2, 1, 2, 2, 2, 1], //maj
		1: [2, 1, 2, 2, 1, 2, 2]  //min
		]

	Map<String, String> commands_abbr = [
		"rel": "relative", 
		"def": "define",
		"part": "part",
		"v": "voice",
		"time": "timeSignature",
		"key": "keySignature",
		"instr": "instrument",
		"cc": "controlChange",
		"call": "callSymbol", 
		"expand": "callSymbol"
		]
	
	Map<String, Map<String, Object>> extMethods = [:]
    
    static final MirChordGrammar grammar= MirChordGrammar.INSTANCE
    
    MirChordProcessor(List extensions=[]) {
    	super()
    	processExtMethods(this)
		extensions.each { ext ->
    		processExtMethods(ext)
    	}
        environments = new Stack()
        environments.add([:].withDefault{[:]})
    }
	
	void printChildren(name, children) {
		println "--------------------"
		println "MATCH: $name"
		children.each { Match m ->
			println "____________________"
			println "child: ${m.getText()}"
			println "parser: ${m.parser}"
			println getResult(m)
			println "____________________"
		}
		println "--------------------"
	}
	

	private void processExtMethods(Object xobj) {
		Method[] methods = xobj.getClass().getDeclaredMethods()
		for(Method method : methods) {
			//println method.getName() + " " + method.isSynthetic()+ " " + method.getModifiers() + " " + method.getAnnotation(MirChord)
			if (!method.isSynthetic() && (method.getModifiers() == Modifier.PUBLIC) 
            		&& (method.getAnnotation(MirChord) != null)) {
				Map<String, Object> map = [:]
				map.put("method", method)
				map.put("object", xobj)
				extMethods.put(method.getName(), map)
			}
		}
	}
	
	private Map getScope() {
		if (environments.size() > 0)
			return environments.peek()
	}
	
	private Object getVarFromScopes(name) {
		Object obj = null
		for(Map env : environments[-1..0]) {
			if (env.containsKey(name)) {
				obj = env[name]
				break
			}
		}
		return obj
	}
	
	private void setVarFromScopes(String name, Object value) {
		for(Map env : environments[-1..0]) {
			if (env.containsKey(name)) {
				env[name] = value
				break
			}
		}
	}

	private void stem(String val) {
		Map scope = getScope()
		StemDirection stemDir = StemDirection.valueOf(val.toUpperCase())
		scope['stem'] = stemDir
	}

	private void relative(String octave) {
		Map scope = getScope()
		scope['octave'] = Integer.parseInt(octave)
		scope['relative'] = null
	}

	private void addToScore(MusicElement element) {		
		String voiceId = currentVoice[currentPart]
		score.parts[currentPart].voices[voiceId].elements << element
		println "addToScore $element  in  $currentPart    $voiceId"
	}

	// COMMANDS

	@MirChord 
	void setCurrentVoice(String id) {
		// println "SETTING VOICE " + id
		if (!score.parts[currentPart].voices.containsKey(id)) {
			score.parts[currentPart].voices.put(id, new Voice(id))
			score.parts[currentPart].voices[id].elements = []
			currentVoice.put(currentPart, id)
		}
		currentVoice[currentPart] = id
	}
	
	@MirChord
	void setCurrentPart(String id) {
		// println "SETTING PART " + id
		if (!score.parts.containsKey(id)) {			
			score.parts.put(id, new Part(id))
		}
		currentPart = id 
	}
	
	@MirChord
	Instrument unpitched(String name, String displayPitch, int octave) {
		Instrument instr = new Instrument(name, true)
		String letter = displayPitch[0]
		int alteration = 0
		for(String alt : displayPitch[1..-1]) {
			if (alt == "#")
				alteration += 1
			else if (alt == "&")
				alteration -= 1

		}
		// update scope
		Map scope = getScope()
		Pitch pitch = new Pitch(letter, alteration, octave)
		scope['displayPitch'] = pitch
		return instr
	}
	
	@MirChord
	Instrument instrument(Integer program) {
		new Instrument(program)
	}
	
	@MirChord
	Instrument instrument(String name) {
		new Instrument(name)			
	}
	
	@MirChord
	Clef clef(String name) {
		ClefType clefType = ClefType.valueOf(name.toUpperCase())
		new Clef(clefType)
	}
	
	@MirChord
	void label(name) {
		Map scope = getScope()
		scope['label'] = name
	}
	
	@MirChord
	Lyrics lyrics(String text) {
		return new Lyrics(text)
	}
	
	@MirChord
	CompositionInfo header(Map... args) {
		// Collection maps = args.flatten()
		CompositionInfo info =  new CompositionInfo()
		for(Map map : args) {
			if (map.containsKey('title'))
				info.title = (String)map['title']
			if (map.containsKey('composer'))
				info.composer = (String)map['composer']
			if (map.containsKey('poet'))
				info.poet = (String)map['poet']
			if (map.containsKey('chordsMode'))
				info.chordsMode = (ChordsMode)map['chordsMode']
		}
		/*info.title =  maps.find { Map it -> it.containsKey('title') }?.title
		info.composer =  maps.find { Map it -> it.containsKey('composer') }?.composer
		info.poet =  maps.find { Map it -> it.containsKey('poet') }?.poet
		info.chordsMode = maps.find { Map it -> it.containsKey('chordsMode') }?.chordsMode*/
		return info
	}
	
	@MirChord
	Map<String, String> title(val) {
		return ['title':val]
	}
	
	@MirChord
	Map<String, String> composer(val) {
		return ['composer':val]
	}
	
	@MirChord
	Map<String, String> poet(val) {
		return ['poet':val]
	}
	
	@MirChord
	Map<String, ChordsMode> chordsMode(String val) {
		ChordsMode mode = ChordsMode.valueOf(val.toUpperCase())
		return ['chordsMode': mode]
	}
	
	@MirChord
	void define(String id, Phrase phrase) { // List<MusicElement> elements
		Map scope = getScope()
		scope[id] = phrase
	}
	
	@MirChord
	Tempo tempo(String tmp) {
		return new Tempo(tmp)
	}
	
	@MirChord
	TimeSignature timeSignature(int numerator, int denominator) {
		Fraction time = fr(numerator, denominator)
		new TimeSignature(time)
	}
	
	@MirChord	
	KeySignature keySignature(String key, String mode) {
		KeySignature keySig = new KeySignature(key, mode)
		Map scope = getScope()
		scope['keySignature'] = keySig
		return keySig
	}
	
	@MirChord
	ControlChange controlChange(int index, int value) {
		new ControlChange(index, value)
	}
	
	@MirChord
	// TODO
	Phrase repeat(int n, Phrase phrase) {
		Phrase rep = phrase // TODO deepcopy(phrase)
		(1..n-1).each { 
			rep.elements.addAll(phrase.elements)
		}
		return rep
	}
	
	@MirChord
	Phrase callSymbol(Phrase phrase) {
		return phrase
	}
	
	@MirChord
	Tuplet tuplet(String ratio, List<Chord> chords) {
		String[] parts =  ratio.split('/')
		int num = Integer.parseInt(parts[0])
		int den = Integer.parseInt(parts[1])
		Fraction fraction = fr(num, den)
		return new Tuplet(fraction, chords)
	}

	// END COMMANDS

	// VISITORS METHODS
	      
    void completeSexpr(Match match) {
		List<Match> children = match.children
		//printChildren(match.getText(), children)
		String cmd = ""
		List parms = []

		cmd = match.findMatchByType(grammar.command).getText()
		List<Match> parms_matches = match.findAllMatchByType(grammar.parm)
		for(Match pm : parms_matches) {
			def res = getResult(pm)
			if (res != null)
				parms << res
		}

		if (commands_abbr.containsKey(cmd))
			cmd = commands_abbr[cmd]
		
		if (extMethods.containsKey(cmd)) {
			// println parms
			Method meth = (Method)extMethods[cmd]["method"]
			if (meth.getReturnType() == void)
				extMethods[cmd]["object"].invokeMethod(cmd, parms)
			else {
				def res = extMethods[cmd]["object"].invokeMethod(cmd, parms)
				putResult(res)
			} 
		} else
			throw new Exception("not found command named $cmd")
    }
	
	static int getAlterationFromAccidentals(List<ACCIDENTALS> accidentals) {
		int alteration = 0
		if (accidentals != null) {
			for(ACCIDENTALS alter : accidentals) {
				switch(alter) {
					case ACCIDENTALS.FLAT:
						alteration -= 1
					break
					case ACCIDENTALS.SHARP:
						alteration += 1
					break
					case ACCIDENTALS.NATURAL:
						alteration = 0
					break
					default:
					break
				}
			}
		}
		return alteration
	}
	
    void completeSharp(Match match) {
        putResult(ACCIDENTALS.SHARP)
    }
    
    void completeFlat(Match match) {
        putResult(ACCIDENTALS.FLAT)
    }
    
    void completeNatural(Match match) {
        putResult(ACCIDENTALS.NATURAL)
    }
    
    void completeAccidental(Match match) {
        putResult(getResult(match.children[0]))
    }
    
    void completeAccidentals(Match match) {
        List<Match> children = match.children
        List<ACCIDENTALS> list = []
        for(Match child : children) {
			list << (ACCIDENTALS)getResult(child)
        }
        putResult(list)
    }
    
    void completeOctaveUp(Match match) {
        putResult(1)
    }
    
    void completeOctaveDown(Match match) {
        putResult(-1)
    }
    
    void completeOctave(Match match) {
        putResult(getResult(match.children[0]))
    }
    
    void completeOctaves(Match match) {
        List<Match> children = match.children
        List<Integer> list = []
        for(Match child : children) {
			list << (int)getResult(child)
        }
        putResult(list)
    }

    void completePitchName(Match match) {
        putResult(match.getText())
    }
	
	void processPitch(Pitch pitch, int octaveSteps, int alterations) {
		Map scope = getScope()
		def scopeOctave = getVarFromScopes('octave')
		if (scopeOctave != null) {
			int octave = (int)scopeOctave
			String symbol = getVarFromScopes('symbol')
			if (symbol) {
				int numNote = NOTE_NAMES[pitch.symbol]
				int relNumNote = NOTE_NAMES[symbol]
				//[C: 1, D: 2, E: 3, F: 4, G: 5, A: 6, B: 7]
				if (Math.abs(numNote - relNumNote) > 3) {
					if ((numNote - relNumNote) < 0)
						octaveSteps += 1
					else
						octaveSteps -= 1
				}
				pitch.octave = octave + octaveSteps
			} else {
				pitch.octave = octave
				scope['symbol'] = pitch.symbol
			}
		}
		else {
			pitch.octave += octaveSteps
			scope['octave'] = pitch.octave
		}
		// TODO
		/*if (phraseType == EventListType.SIMULTANEOUS) {
			scope['octave'] = pitch.octave
			scope['symbol'] = pitch.symbol
		}
		else {*/
			setVarFromScopes('octave', pitch.octave)
			setVarFromScopes('symbol', pitch.symbol)
		/*}*/
		
		if (alterations != 0)
			pitch.alteration = alterations
			
		KeySignature currentKey = (KeySignature)getVarFromScopes('keySignature')
		if (currentKey != null) {
			int keysig = currentKey.getFifths()
			pitch.alterForKeySignature(keysig)
		}
	}
    
    void completePitch(Match match) {
        List<Match> children = match.children
		
		List<ACCIDENTALS> accidentals = []
		List<Match> accidentals_match = match.findAllMatchByType(grammar.accidental)
		for(Match acc : accidentals_match) {
			accidentals << (ACCIDENTALS)getResult(acc)
		}
			
		String pitchLetter = ((String)getResult(match.findMatchByType(grammar.pitchName))).toUpperCase()
		Pitch pitch
		if (pitchLetter == "X") {
			pitch = (Pitch)getVarFromScopes('displayPitch')
			// println "Try to get displayPitch from scope " + pitch
			if (pitch == null)
				pitch = new Pitch()
		} else {
			pitch = new Pitch(pitchLetter)
			Match octaves_match = match.findMatchByType(grammar.octaves)
			List<Integer> octaveSteps = (List<Integer>)getResult(octaves_match)
			if (octaveSteps == null || octaveSteps.size() == 0)
				octaveSteps = [0]			
			/*
			 first check rel octave in scope
			 if present check symbol (C, A, ecc..) in scope
			 if both present then as usual (lilypond - fifth distance)
			 otherwise set the octave as the one from scope 
			 
			 if the octave is not present find in parent scope 
			 if simultan then create octave in local scope
			 
			 for duration similar thing with stickyDuration in scope
			*/
				
			int alterations = (accidentals != null) ? getAlterationFromAccidentals(accidentals) : 0
			processPitch(pitch, (int)octaveSteps.sum(), alterations)	
		}			
		
		putResult(pitch)
    }
	
	// TODO: verify
	void completeDuration(Match match) {
		int base_duration = (int)getResult(match.findMatchByType(grammar.number))
		List<Match> dots = match.findAllMatchByType(grammar.dot)
		for(Match dot : dots) {
			base_duration += (int)(base_duration / 2)
		}
		putResult(fr(1,base_duration))
	}
	
	void completeVelocity(Match match) {
		putResult(match.getText()[1..-1])
	}
	
	void completeTieStart(Match match) {
		putResult(true)
	}
	
	void completeTieEnd(Match match) {
		putResult(true)
	}

	void completePitchList(Match match) {
		List<Match> pitches_match = match.findAllMatchByType(grammar.pitch)
		List<Pitch> pitches = []
		for(Match m : pitches_match) {
			Pitch pitch = (Pitch)getResult(m)
			pitches << pitch
		}
        putResult(pitches)
    }
	
	void completeChord(Match match) {
		List<Pitch> pitchList = (List<Pitch>)getResult(match.findMatchByType(grammar.pitchList))
		if (pitchList == null || pitchList.size() == 0) {
			Pitch pitch = (Pitch)getResult(match.findMatchByType(grammar.pitch))
			pitchList = [pitch]
		}
		Chord chord = new Chord()	
		chord.pitches = pitchList	
		Fraction scopeDuration = (Fraction)getVarFromScopes('duration')
		if (scopeDuration)
			chord.duration = scopeDuration			
		Match duration_match = match.findMatchByType(grammar.duration)
		Fraction duration = (Fraction)getResult(duration_match)
		if (duration != null) {
			chord.duration = duration
			// update sticky duration
			if (scopeDuration)
				setVarFromScopes('duration', duration)
			else {
				Map scope = getScope()
				scope['duration'] = duration
			}
		}
		StemDirection stemDir = (StemDirection)getVarFromScopes('stem')
		if (stemDir)
			chord.setStem(stemDir)
		putResult(chord)
	}

	void completeRest(Match match) {
		Rest rest = new Rest()
		Match duration_match = match.findMatchByType(grammar.duration)
		Fraction duration = (Fraction)getResult(duration_match)
		rest.duration = duration
		putResult(rest)
	}
    
	void completeRelativeOctave(Match match) {
		relative(match.getText()[1..-1])
	}
	
	void completeStickyDuration(Match match) {
		Fraction duration = (Fraction)getResult(match.findMatchByType(grammar.duration))
		scope["duration"] = duration
	}
	
	void completeDot(Match match) {
		putResult(match.getText())
	}
	
	void completeAtom(Match match) {
		Chord chord = (Chord)getResult(match.findMatchByType(grammar.chord))
		if (chord != null) {
			boolean tieStart = (boolean)getResult(match.findMatchByType(grammar.tieStart))
			boolean tieEnd = (boolean)getResult(match.findMatchByType(grammar.tieEnd))
			def dynamicMark = getResult(match.findMatchByType(grammar.velocity))			
			if (dynamicMark != null)
				chord.dynamicMark = dynamicMark			
			if (tieStart)
				chord.tieStart = true
			if (tieEnd)
				chord.tieEnd = true
			putResult(chord)
		} 
		ChordSymbol chordSym = (ChordSymbol)getResult(match.findMatchByType(grammar.chordSymbol))
		if (chordSym != null) {
			putResult(chordSym)
		}
		Rest rest = (Rest)getResult(match.findMatchByType(grammar.rest))
		if (rest != null) {
			putResult(rest)
		}
	}

	void completeMusicElement(Match match) {
		Phrase phrase = (Phrase)getResult(match.findMatchByType(grammar.phrase))
		if (phrase != null) {
			putResult(phrase)
		}
		MusicElement element = (MusicElement)getResult(match.findMatchByType(grammar.atom))
		if (element != null) {
			putResult(element)
		}
	}

	void completePhrase(Match match) {
		List<Match> children = match.findAllMatchByType(grammar.atom)
		Phrase phrase = new Phrase()
		for(Match child : children) {
			Chord res = (Chord)getResult(child) // MusicElement
			if (res != null)
				phrase.elements << res
		}
		putResult(phrase)
	}

	void completeScoreElement(Match match) {
		Match child = match.getFirstChild()
		if (child.parser == grammar.contextElement) {
			putResult(getResult(child))
		}
		if (child.parser == grammar.musicElement) {
			MusicElement element = (MusicElement)getResult(child)
			putResult(element)
		}
		if (child.parser == grammar.sexpr) {
			def obj = getResult(child)
			// if (obj instanceof MusicElement) { ... }
			if (obj != null)
				putResult(obj)
		}
	}

	void completeScore(Match match) {
		List<Match> children = match.findAllMatchByType(grammar.scoreElement)
		for(Match scoreElement : children) {
			def obj = getResult(scoreElement)
			if (obj != null) {
				if (obj instanceof Match) {
					Match m = (Match)obj
					if (m.parser == grammar.part)
						setCurrentPart(m.getText()[1..-1])
					if (m.parser == grammar.voice)
						setCurrentVoice(m.getText()[1..-1])
				} else {
					try {
						MusicElement element = (MusicElement)obj
						if (element != null)
							addToScore(element)
					}
					catch(Exception ex) {
						throw new Exception("Object $obj not a MusicElement")
					}
				}
			}
		}
	}

	void completeContextElement(Match match) {
		Match child = match.getFirstChild()
		putResult(child)
	}

	void completeStem(Match match) {
		Match child = match.getFirstChild()
		String dir = "AUTO"
		if (child.parser == grammar.stemUp)
			dir = "UP"
		if (child.parser == grammar.stemDown)
			dir = "DOWN"
		stem(dir)
	}

	void completeAnchor(Match match) {
		putResult(new Anchor(match.getText()[1..-1]))
	}

	// CHORD SYMBOLS------------------
	
	void completeChordPitchName(Match match) {
		putResult(match.getText())
	}
	
	void completeChordRoot(Match match) {			
		String pitchLetter = getResult(match.findMatchByType(grammar.chordPitchName))
		Pitch pitch = new Pitch(pitchLetter)			
		List<ACCIDENTALS> accidentals = []
		List<Match> accidentals_match = match.findAllMatchByType(grammar.accidental)
		for(Match acc : accidentals_match) {
			accidentals << (ACCIDENTALS)getResult(acc)
		}			
		int octaveSteps = 0			
		int alterations = (accidentals != null) ? getAlterationFromAccidentals(accidentals) : 0
		processPitch(pitch, octaveSteps, alterations)			
		putResult(pitch)
	}		
	
	void completeChordModifier(Match match) {
		putResult(match.getText())
	}
	
	void completeChordExtension(Match match) {
		putResult(match.getText())
	}
	
	void completeChordKind(Match match) {
		ChordKind kind = getChordSymboKind(match.getText())
		putResult(kind)
	}
	
	void completeChordAltOp(Match match) {
		putResult(match.getText())
	}
	
	void completeChordAltDegree(Match match) {
		putResult(match.getText())
	}
	
	void completeChordAlteration(Match match) {
		String oper = getResult(match.findMatchByType(grammar.chordAltOp))
		if (oper == null || oper == "")
			oper = 'alt'
		String alterDg = getResult(match.findMatchByType(grammar.chordAltDegree))
		
		List<ACCIDENTALS> accidentals = []
		List<Match> accidentals_match = match.findAllMatchByType(grammar.accidental)
		for(Match acc : accidentals_match) {
			accidentals << (ACCIDENTALS)getResult(acc)
		}
		
		ChordAlteration chordAlteration = new ChordAlteration(alterDg)
		if (accidentals != null) {
			chordAlteration.accidental = getAlterationFromAccidentals(accidentals)
		}
		chordAlteration.type = ChordAltType.valueOf(oper.toUpperCase())
		putResult(chordAlteration)
	}
	
	void completeChordBassSeparator(Match match) {
		putResult(match.getText())
	}
	
	void completeChordBass(Match match) {
		String pitchLetter = getResult(match.findMatchByType(grammar.chordPitchName))
		putResult(new Pitch(pitchLetter))
	}
	
	void completeChordSymbol(Match match) {
		String chordName = match.getText()
		
		Pitch root = (Pitch)getResult(match.findMatchByType(grammar.chordRoot))
		ChordKind kind = (ChordKind)getResult(match.findMatchByType(grammar.chordKind))
		ChordAlteration alteration = (ChordAlteration)getResult(match.findMatchByType(grammar.chordAlteration))
		String bassSeparator = getResult(match.findMatchByType(grammar.chordBassSeparator))
		Pitch bass = (Pitch)getResult(match.findMatchByType(grammar.chordBass))
		
		ChordSymbol chordSym = new ChordSymbol(root, kind)
		chordSym.setText(chordName)
		if (bass != null) {
			chordSym.bass = bass
			if (bassSeparator != null && bassSeparator == "\\")
				chordSym.upInversion = false
		}
		if (alteration != null)
			chordSym.chordAlteration = alteration

		Fraction duration = (Fraction)getVarFromScopes('duration')
		if (duration != null)
			chordSym.duration = duration
		else
			chordSym.duration = fr(1,1)			
		// update sticky duration
		setVarFromScopes('duration', duration)

		scope['currentChordSymbol'] = chordSym
		putResult(chordSym)
	}
	
	void completeSameChordSymbol(Match match) {
		ChordSymbol curChordSymbol = (ChordSymbol)getVarFromScopes('currentChordSymbol')
		if (curChordSymbol != null) 
			putResult(curChordSymbol)
	}
	
	//END CHORD SYMBOLS -----------------------
	
    void completeParm(Match match) {
		putResult(getResult(match.children[0]))
    }
    
	void completeDecimal(Match match) {
		putResult(new BigDecimal(match.getText()))
	}
	
	void completeNumber(Match match) {
		putResult(Integer.parseInt(match.getText()))
	}
	
	void completeSymbol(Match match) {
		putResult(match.getText())
	}
	
	void completeStringa(Match match) {
		putResult(match.getText()[1..-2])
	}
	
	void completeIdentifier(Match match) {
		def sym = match.getText()[1..-1]
		def scope = getScope()
		if (scope.containsKey(sym)) {
			def clone = scope[sym] // TODO: deepcopy(scope[sym])
			putResult(clone)
		}
		else
			putResult(sym)
	}

	// END VISITORS METHODS

    public Score process(ParseResults results) {
    	score = new Score()
        getResult(results.getLongestMatch())
        return score
    }
}

