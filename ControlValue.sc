AbstractControlValue {
    var value, <spec, specConnection, <>updateOnConnect=true, <>holdUpdates=false;
    var >metadata;
    var inputTransform;
    var prIsChanging=false;
    
    *defaultSpec { 
        this.subclassResponsibility(thisMethod) 
    }
    
    *new {
        |initialValue, spec|
        ^super.new.init(initialValue, spec);
    }
    
    init {
        |initialValue, inSpec|
        spec	= inSpec !? { inSpec.asSpec } ?? { this.class.defaultSpec.deepCopy };
        value	= initialValue;
        this.constrain(true);
    }
    
    // Metadata
    metadata { 		^(metadata ?? { metadata = () }) }
    md { 			^this.metadata }
    md_{ |md| 		^this.metadata_(md) }
    name {			^this.metadata[\name] ?? { spec.tryPerform(\units) } }
    name_{ |value| 	 this.metadata[\name] = value }
    color {			^this.metadata[\color] }
    color_{ |value|  this.metadata[\color] = value }
    group {         ^this.metadata[\group] }
    
    emitChanged {
        var input = this.input;
        this.changed(\input, input);
        this.changed(\value, this.value, input);
    }
    
    value {
        ^(value ?? { spec.default })
    }
    
    value_{
        |inVal|
        inVal = spec.constrain(inVal);
        if (inVal != value) {
            value = inVal;
            this.emitChanged();
        }
    }
    
    input_{
        |inVal|
        this.value = spec.map(inVal);
    }
    
    input {
        ^spec.unmap(this.value);
    }
    
    defaultIncrement {
        ^(spec.step > 0).if(spec.step, 1/200);
    }
    
    increment {
        |amount|
        amount = amount ?? { this.defaultIncrement };
        this.input = this.input + amount;
    }
    
    decrement {
        |amount|
        amount = amount ?? { this.defaultIncrement };
        ^this.increment(amount.neg);
    }
    
    addDependant {
        |dependant|
        super.addDependant(dependant);
        
        this.onSignalDependantAdded(\value, dependant)
    }
    
    onSignalDependantAdded {
        |signal, dependant|
        var value, input;
        if (updateOnConnect) {
            value = this.value;
            input = this.input;
            if (signal == \input) {
                dependant.update(this, \input, input);
            } {
                dependant.update(this, \value, value, input);
            }
        }
    }
    
    spec_{
        |inSpec|
        inSpec = inSpec.asSpec;
        if (spec.isKindOf(inSpec.class)) {
            spec.setFrom(inSpec.asSpec);
        } {
            spec = inSpec;
        };
        
        this.constrain();
    }
    
    constrain {
        |notify=true|
        
        if (value.notNil) {
            value = spec.constrain(value);
        };
        
        if (notify) {
            this.emitChanged();
        }
    }
    
    changed {
        arg what ... moreArgs;
        if (holdUpdates.not && prIsChanging.not) {
            prIsChanging = true;
            protect {
                super.changed(what, *moreArgs);
            } {
                prIsChanging = false;
            }
        }
    }
    
    // Do not override this method in subclasses - instead, override prSetFrom
    setFrom {
        |other|
        if (this.class != other.class) {
            Error("Trying to set a ControlValue of type '%' from one of type '%'.".format(this.class, other.class)).throw
        } {
            this.holdUpdates = true;
            protect { this.prSetFrom(other) } {
                this.holdUpdates = false;
            };
            this.emitChanged();
        }
    }
    
    prSetFrom {
        |other|
        this.updateOnConnect	= other.updateOnConnect;
        this.spec				= other.spec;
        this.value				= other.value;
    }
    
    asControlInput {
        ^this.value
    }
    
    asPattern {
        |initialValue|
        if (initialValue.isKindOf(Symbol)) {
            ^Prout({
                |event|
                event[initialValue] !? {
                    |v|
                    this.value = v;
                };
                
                loop {
                    this.asControlInput.yield
                }
            })              
        } {
            ^Prout({
                if (initialValue.notNil) {
                    this.value = initialValue;
                };
                
                loop {
                    this.asControlInput.yield
                }
            })                 
        }
    }
    
    asValuePattern {
        |initialValue|
        if (initialValue.isKindOf(Symbol)) {
            ^Prout({
                |event|
                event[initialValue] !? {
                    |v|
                    this.value = v;
                };
                
                loop {
                    this.value.yield
                }
            }) <> initialValue
        } {
            ^Prout({
                if (initialValue.notNil) {
                    this.value = initialValue;
                };
                
                loop {
                    this.value.yield
                }
            })  
        }
    }
    
    asValueStream {
        |initialValue|
        ^this.asValuePattern(initialValue).asStream
    }
    
    asStream {
        |initialValue|
        ^this.asPattern(initialValue).asStream
    }
    
    free {
        this.releaseDependants();
    }
    
    embedInStream {
        |inval|
        ^this.value.yield;
    }
    
    ccRel_{
        |ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast=false|
        ^MIDIControlValue().ccRel_(ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast).mapTo(this);
    }
    
    cc14_{
        |ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast=false|
        ^MIDIControlValue().cc14_(ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast).mapTo(this);
    }
    
    cc_{
        |ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast=false, midiMethod=(\cc)|
        ^MIDIControlValue().cc_(ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast, midiMethod).mapTo(this);
    }
}

NumericControlValue : AbstractControlValue {
    *defaultSpec { ^\unipolar.asSpec }
}

IndexedControlValue : AbstractControlValue {
    *defaultSpec { 
        ^ItemsSpec([]) 
    }
    
    next {
        var index;
        if (spec.items.size > 0) {
            index = spec.items.indexOf(this.value);
            this.value = spec.items.wrapAt(index + 1);
        }
    }
    
    prev {
        var index;
        if (spec.items.size > 0) {
            index = spec.items.indexOf(this.value);
            this.value = spec.items.wrapAt(index - 1);
        }
    }
}

BusControlValue : NumericControlValue {
    var bus, <>server, <channels;
    
    init {
        |initialValue, inSpec|
        super.init(initialValue, inSpec);
        
        server = Server.default;
        
        ServerTree.add(this);
        ServerQuit.add(this);
        ServerBoot.add(this);
        
        channels = initialValue !? { initialValue.asArray.size } ?? 1;
        
        if (Server.default.serverRunning) {
            this.doOnServerBoot();
            this.doOnServerTree();
        }
    }
    
    bus {
        if (bus.isNil && server.serverRunning) {
            this.sendBus();
        };
        ^bus;
    }
    
    doOnServerTree {}
    
    doOnServerBoot {
        this.sendBus();
    }
    
    doOnServerQuit {
        this.free();
    }
    
    value_{
        |inValue|
        super.value_(inValue);
        this.prSendValue()
    }
    
    constrain {
        super.constrain();
        this.prSendValue()
    }
    
    sendBus {
        if (bus.isNil) {
            bus = Bus.control(server, 1);
            this.prSendValue();
        }
    }
    
    prSendValue {
        bus !? {
            bus.server.makeBundle(nil, {
                var value = this.value.asArray;
                value = value.collect {
                    |v|
                    if (v.isNumber) {
                        v
                    } {
                        spec.items.indexOf(v)
                    }
                };
                
                bus.set(*value)
            });
        };
    }
    
    free {
        super.free();
        bus.free;
        bus = nil;
    }
    
    asMap 				{ ^this.bus.asMap }
    asControlInput		{ ^this.asMap }
    asBus 				{ ^this.bus }  
    ar                  { |numChannels=(bus.numChannels), offset=0| ^this.bus.ar(numChannels, offset) }
    kr                  { |numChannels=(bus.numChannels), offset=0| ^this.bus.kr(numChannels, offset) }
}

SynthControlValue : BusControlValue {
    classvar <group;
    classvar <>dbCurveAdjust=2;
    
    var input, <inputFunc, <valueFunc, <def, <lag=0,
        <synth, <updater, <pollRate=30,
        inputTransform, valueTransform;
    var queue;
    
    *defaultSpec { 
        ^\unipolar.asSpec;
    }
    
    *initClass {
        ServerTree.add(this);
    }
    
    *doOnServerTree {
        group = Group(Server.default.asGroup, \addBefore);
    }
    
    makeDef {
        var hash = [valueFunc !? _.def,  inputFunc !? _.def].hash;
        var name = "SynthControlValue_" ++ hash.asString;
        
        ^SynthDef(name.asSymbol, {
            |prOut, prInput, prValue, prLag=0, prSpecMin, prSpecMax, prIsDB|
            var value = prValue;
            
            if (inputFunc.notNil) {
                value = prInput;
                value = SynthDef.wrap(inputFunc, nil, [value]);
                value = (prIsDB > 0).if(
                    value
                        .lincurve(0, 1, 0, 1, dbCurveAdjust)
                        .lag(prLag)
                        .curvelin(0, 1, 0, 1, dbCurveAdjust),
                    value.lag(prLag)
                );
                value = spec.map(value);
            };
            
            if (valueFunc.notNil) {
                value = SynthDef.wrap(valueFunc, nil, [value]);
            };
            
            if (inputFunc.isNil) {
                value = (prIsDB > 0).if(
                    value
                        .lincurve(prSpecMin, prSpecMax, 0, 1, dbCurveAdjust)
                        .lag(prLag)
                        .curvelin(0, 1, prSpecMin, prSpecMax, dbCurveAdjust),
                    value.lag(prLag)
                );
            };
            
            value = value.asArray[0..channels];
            value = value.clip(prSpecMin, prSpecMax);
            
            Out.kr(prOut, value)
        }).add;
    }
    
    init {
        |initialValue, inSpec|
        queue = ServerMessageQueue(Server.default);
        queue.latency = nil;
        def = this.makeDef({ |val| val });
        updater = BusUpdater(nil, 1 / pollRate);
        
        super.init(initialValue, inSpec);
    }
    
    set {
        |...pairs|
        synth !? {
            queue.makeBundle({
                synth.set(*pairs)
            });
        }
    }
    
    lag_{
        |value|
        lag = value;
        synth !? {
            synth.set(\prLag, value);
        }
    }
    
    doOnServerBoot {
        super.doOnServerBoot();
        updater.bus = this.bus;
        updater.start();
    }
    
    doOnServerTree {
        super.doOnServerTree();
        this.sendSynth();
    }
    
    doOnServerQuit {
        super.doOnServerQuit();
        updater.bus = nil;
        updater.stop();
        this.freeSynth();
    }
    
    func_{
        |func|
    }
    
    valueFunc_{
        |func|
        valueFunc = func;
        this.def = this.makeDef();
    }
    
    inputFunc_{
        |func|
        inputFunc = func;
        this.def = this.makeDef();
    }
    
    def_{
        |synthDef|
        def = synthDef;
        this.sendSynth();
    }
    
    bus {
        ^bus;
    }
    
    preValue {
        ^value ?? { spec.default }
    }
    
    preInput {
        ^spec.unmap(this.preValue);
    }
    
    value {
        if (bus.notNil and: { bus.server.serverRunning && bus.server.serverBooting.not }) {
            ^bus.getSynchronous()
        } {
            ^super.value;
        }
    }
    
    value_{
        |v|
        super.value_(v);
        input = this.preInput;
    }
    
    increment {
        |amount|
        var currentInput;
        
        amount = amount ?? { this.defaultIncrement };
        currentInput = this.input();
        this.input = currentInput + amount;
    }
    
    spec_{
        |newSpec|
        super.spec_(newSpec);
        this.def = this.makeDef();
    }
    
    sendSynth {
        if (server.serverRunning) {
            queue.makeBundleSync {
                synth !? { synth.free };
                synth = def.play(group, [
                    \prOut, 		bus,
                    \prInput,		this.preInput,
                    \prValue, 		this.preValue,
                    \prLag, 		lag,
                    \prSpecMin,		spec.minval,
                    \prSpecMax,		spec.maxval,
                    \prIsDB,        spec.warp.isKindOf(DbFaderWarp).if(1, 0)
                ], \addToTail);
            };
            updater.start;
        }
    }
    
    freeSynth {
        synth !? {
            queue.makeBundleSync {
                synth.free; synth = nil;
            };
            updater.stop;
        };
    }
    
    free {
        super.free();
        this.freeSynth();
    }
    
    prSendValue {
        synth !? {
            queue.makeBundle {
                synth.set(\prInput, this.preInput, \prValue, this.preValue)
            }
        };
    }
    
    signal {
        |keyOrFunc|
        switch (keyOrFunc,
            \value, {
                valueTransform = valueTransform ?? {
                    updater.signal(\value).transform({
                        |obj, what, val|
                        [obj, what, val, spec.unmap(val)]
                    })
                };
                ^valueTransform;
            },
            \preValue, { ^super.signal(\value) },
            \preInput, { ^super.signal(\input) },
            {
                ^super.signal(keyOrFunc);
            }
        );
    }
}

ArrayControlValue : AbstractControlValue {
    var value, onSig, offSig;
    
    *defaultSpec { 
        ^ItemSpec([\a, \b, \c, \d]) 
    }
    
    *new {
        |default, spec|
        if (spec.isArray) {
            spec = ItemSpec(spec)
        };
        
        ^super.new(default, spec)
    }
    
    items { ^spec.items }
    
    // increment {
    // 	this.value = spec.items.wrapAt(spec.items.indexOf(this.value) !? (_+1) ?? {0});
    // }
    //
    // decrement {
    // 	this.value = spec.items.wrapAt(spec.items.indexOf(this.value) !? (_-1) ?? {0});
    // }
    //
    // value_{
    // 	|inVal|
    // 	if (spec.items.includes(inVal)) {
    // 		super.value_(inVal)
    // 	} {
    // 		Exception("Value must be: %".format(spec.items.join(", "))).throw
    // 	}
    // }
}


OnOffControlValue : ArrayControlValue {
    var value, onSig, offSig;
    
    *defaultSpec { 
        ^ItemSpec([\off, \on]) 
    }
    
    on {
        this.value = \on;
    }
    
    off {
        this.value = \off;
    }
    
    toggle {
        this.value = (value == \on).if(\off, \on);
    }
}

MIDIControlValue : NumericControlValue {
    classvar <>broadcastMuteTime=0.2, <>mappings;
    // classvar <cc14Max = 8192; // (128*128) - 1 workaround eletra bug...
    classvar <cc14Max = 16383;
    var <>inputSpec, <isOwned=false;
    var func, <midiFunc, broadcastDest, broadcastFunc, lastInEvent=0;
    
    *defaultInputSpec { 
        ^ControlSpec(0, 127); 
    }
    
    *initClass {
        mappings = ();
    }
    
    mapTo {
        |cv|
        this.updateOnConnect = false;
        mappings[cv].free;
        mappings[cv] = ConnectionList [
            this.signal(\input).connectTo(cv.inputSlot),
            cv.signal(\input).connectToUnique(\broadcast, broadcastFunc);
        ];
        ^mappings[cv]
    }
    
    broadcast_{
        |destination|
        if (destination.isKindOf(MIDIEndPoint).not) {
            destination = MIDIClient.destinations.detect({ |s| s.uid == destination });
        };
        
        if (destination.isNil) {
            broadcastDest = nil;
        } {
            broadcastDest = MIDIOut.newByName(destination.device, destination.name).latency_(0);
        }
    }
    
    broadcast { ^broadcastDest }
    
    cc14_{
        |ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast=false|
        inputSpec = inputSpec ?? { ControlSpec(0, MIDIControlValue.cc14Max) };
        ^this.cc_(ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast, \cc14)
    }
    
    cc_{
        |ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast=false, midiMethod=(\cc)|
        var canBroadcast;
        inputSpec = inputSpec ?? { this.class.defaultInputSpec };
        
        func = func ? {
            |val|
            // "midi in [cc=%]: %".format(ccNumOrFunc, val).postln;
            lastInEvent = thisThread.clock.seconds;
            this.input = inputSpec.unmap(val);
        };
        
        this.clearMIDIFunc();
        
        if (ccNumOrFunc.notNil) {
            if (ccNumOrFunc.isKindOf(MIDIFunc)) {
                isOwned = false;
                midiFunc = ccNumOrFunc;
                midiFunc.add(func);
            } {
                isOwned = true;
                midiFunc = MIDIFunc.perform(midiMethod, func, ccNumOrFunc, chan, srcID, argTemplate, dispatcher);
                midiFunc.permanent_(true)
            };
            
            this.prSetBroadcastFunc(ccNumOrFunc, chan, (midiMethod==\cc14));
            if (broadcast) { this.broadcast = this.prFindDestForSource(srcID) };
        }
    }
    
    ccRel_{
        |ccNumOrFunc, chan, srcID, argTemplate, dispatcher, broadcast=false|
        var canBroadcast, midiMethod=\cc;
        inputSpec = inputSpec ?? { this.class.defaultInputSpec };
        
        // Uses "binary offset" relative, around 64
        func = func ? {
            |val|
            val = (val - 64.0) / 64.0;
            // "relative: %".format(val).postln;
            lastInEvent = thisThread.clock.seconds;
            this.increment(val);
        };
        
        this.clearMIDIFunc();
        
        if (ccNumOrFunc.notNil) {
            if (ccNumOrFunc.isKindOf(MIDIFunc)) {
                isOwned = false;
                midiFunc = ccNumOrFunc;
                midiFunc.add(func);
            } {
                isOwned = true;
                midiFunc = MIDIFunc.perform(midiMethod, func, ccNumOrFunc, chan, srcID, argTemplate, dispatcher);
                midiFunc.permanent_(true)
            };
            
            this.prSetBroadcastFunc(ccNumOrFunc, chan, (midiMethod==\cc14));
            if (broadcast) { this.broadcast = this.prFindDestForSource(srcID) };
        }
        
    }
    
    prFindDestForSource {
        |srcID|
        var dest;
        dest = MIDIClient.sources.detect({ |s| s.uid == srcID });
        dest = MIDIClient.destinations.detect({ |d| (dest.device == d.device) && (dest.name == d.name) });
        ^dest.uid
    }
    
    prSetBroadcastFunc {
        |ccNumOrFunc, chan, cc14=false|
        var canBroadcast = (
            ccNumOrFunc.isKindOf(Number) || ccNumOrFunc.isKindOf(Collection)
                and: { chan.isKindOf(Number) }
        );
        // "canBroadcast: %, ccNumOrFunc: %  chan: %".format(canBroadcast, ccNumOrFunc, chan).postln;
        
        
        if (canBroadcast) {
            var method;
            
            method = cc14.if(\control14, \control);
            
            broadcastFunc = {
                |what, change, input|
                if (broadcastDest.notNil) {
                    if ((thisThread.clock.seconds - lastInEvent) > broadcastMuteTime) {
                        // "midi -> [cc=%]: %".format(ccNumOrFunc, inputSpec.map(input)).postln;
                        broadcastDest.perform(method,
                            chan, ccNumOrFunc, inputSpec.map(input).round.asInteger
                        )
                    }
                }
            };
            
            this.signal(\input).connectToUnique(\prBroadcast, broadcastFunc)
        } {
            this.signal(\input).uniqueConnectionAt(\prBroadcast).free();
            if (broadcastDest.notNil) {
                ".broadcast is specified, but not enough information to produce a MIDIOut (cc, chan, and srcID must be specified)".warn;
            }
        }
    }
    
    prSetFrom {
        |other|
        super.prSetFrom(other);
        this.inputSpec = other.inputSpec;
        if (other.midiFunc.notNil) {
            this.cc_(
                other.midiFunc.msgNum,
                other.midiFunc.chan,
                other.midiFunc.srcID,
                other.midiFunc.argTemplate,
                other.midiFunc.dispatcher
            )
        }
    }
    
    free {
        this.clearMIDIFunc();
    }
    
    clearMIDIFunc {
        if (midiFunc.notNil) {
            midiFunc.remove(func);
            if (isOwned) {
                midiFunc.free;
            };
            midiFunc = nil;
        }
    }
}

OSCControlValue : NumericControlValue {
    var <inputSpec, <isOwned=false;
    var func, <oscFunc, <index=1;
    
    *defaultInputSpec { 
        ^nil 
    }
    
    index_{
        |i|
        index = i;
    }
    
    src_{
        | pathOrFunc, srcID, recvPort, argTemplate, dispatcher |
        inputSpec = inputSpec ?? { this.class.defaultInputSpec };
        
        func = func ? {
            |val|
            index !? { val = val[index] };
            if (inputSpec.notNil) {
                this.input = inputSpec.unmap(val);
            } {
                this.value = val;
            }
        };
        
        this.clearOSCFunc();
        
        if (pathOrFunc.notNil) {
            if (pathOrFunc.isKindOf(OSCFunc)) {
                isOwned = false;
                oscFunc = pathOrFunc;
                oscFunc.add(func);
            } {
                isOwned = true;
                oscFunc = OSCFunc(func, pathOrFunc, srcID, recvPort, argTemplate, dispatcher);
            }
        }
    }
    
    prSetFrom {
        |other|
        super.prSetFrom(other);
        this.inputSpec = other.inputSpec;
        if (other.oscFunc.notNil) {
            this.src_(
                other.oscFunc.path,
                other.oscFunc.srcID,
                other.oscFunc.recvPort,
                other.oscFunc.argTemplate,
                other.oscFunc.dispatcher
            )
        }
    }
    
    free {
        this.clearOSCFunc();
    }
    
    clearOSCFunc {
        if (oscFunc.notNil) {
            oscFunc.remove(func);
            if (isOwned) {
                oscFunc.free;
            };
            oscFunc = nil;
        }
    }
    
}

ControlValueEnvir : EnvironmentRedirect {
    var <default, redirect;
    var <>allowCreate=true;
    var metadata, storeSource, <storePath, storeUpdater, storeConnections;
    var <>guiFunc;
    var fadeRoutine;
    var groupProperties;
    var presets;
    var groupPrefix;
    
    *new {
        |type=(NumericControlValue)|
        ^super.new(Environment()).default_(type).know_(false)
    }
    
    *newFromSpecs {
        |specs, type|
        ^this.new(type).setSpecs(specs);
    }
    
    // Metadata
    metadata { 			^(metadata ?? { metadata = () }) }
    metadata_{ |md| 	 metadata = md }
    md {				^this.metadata }
    md_{ |md| 			^this.metadata_(md) }
    name {				^this.metadata[\name] }
    name_{ |value| 	 	 this.metadata[\name] = value }
    color {				^this.metadata[\color] }
    color_{ |value|  	 this.metadata[\color] = value }
    
    prResolvePath  {
        |name|
        var parentPath = PathName(thisProcess.nowExecutingPath).parentPath;
        var newStorePath;
        
        newStorePath = Require.resolvePaths(name, [parentPath], ["scd"]);
        
        if (newStorePath.size > 1) {
            "We resolved % possible paths for control storage, using: %".format(newStorePath.size, newStorePath.first).warn;
        };
        
        if (newStorePath.size == 0) {
            newStorePath = parentPath +/+ name ++ ".scd"
        } {
            newStorePath = newStorePath[0];
        };
        
        "Storing control values at: %".format(newStorePath).postln;
        
        ^newStorePath
    }
    
    storeCurrent {
        |name|
        var newStorePath;
        
        name = (name ?? { this.name }) !? format("%_currentState", _) ?? { "currentState" };
        
        newStorePath = this.prResolvePath(name);
        
        if (storePath != newStorePath) {
            storePath = newStorePath;
            this.prConnectToStore(read:File.exists(storePath));
        }
    }
    
    storeStates {
        |bool=true|
        var name = this.name !? format("%_states", _) ?? { "states" };
        
        if (bool) {
            ControlValueEnvirStates(this).storePath = this.prResolvePath(name);
        } {
            ControlValueEnvirStates(this).storePath = nil;
        }
    }
    
    saveState {
        |name, include=true, exclude=false|
        ControlValueEnvirStates(this).saveState(name, include, exclude);
    }
    
    loadState {
        |name|
        ControlValueEnvirStates(this).loadState(name);
    }
    
    prConnectToStore {
        |read=true|
        storeConnections.do(_.free);
        
        if (read) {
            this.prLoadFromStore();
        };
        
        storeUpdater ?? {
            storeUpdater = CollapsedUpdater(5);
            storeUpdater.connectTo(this.methodSlot(\prSaveToStore));
        };
        
        storeConnections = envir.values.collect {
            |cv|
            cv.signal(\value).connectTo(storeUpdater);
        };
        
        if (read.not) {
            this.prSaveToStore();
        }
    }
    
    prSaveToStore {
        storePath !? {
            "Writing control values to: %".format(storePath).postln;
            File(storePath, "w").write(
                "(\n%\n(\n%\n)\n)\n".format(
                    "// saved values for ControlValueEnvir\n// file: %\n".format(
                        storePath
                    ),
                    (
                        this.envir
                            .asAssociations
                            .sort({
                                |a, b|
                                a.key > b.key
                            })
                            .collect({
                                |cvKey|
                                "%: %,".format(
                                    cvKey.key.asString.padLeft(20),
                                    cvKey.value.value.asCompileString
                                )
                            }, Array)
                    ).join("\n")
                )
            ).close();
        }
    }
    
    prLoadFromStore {
        var values;
        storePath.postln;
        storePath !? {
            if (File.exists(storePath)) {
                "Reading control values from: %".format(storePath).postln;
                values = thisProcess.interpreter.executeFile(storePath);
                values.keysValuesDo {
                    |key, value|
                    this.at(key) !? (_.value_(value));
                }
            }
        }
    }
    
    setGroups {
        |event|
        event.keysValuesDo {
            |name, cv, j|
            
            if (cv.isKindOf(Collection).not) {
                cv = [cv]
            };
            
            name = name.asSymbol;
            
            cv = cv.collect {
                |cv, i|
                if (cv.isKindOf(Symbol)) {
                    cv = super.at(cv);
                };
                cv.md[\group] = name;
                cv.md[\order] = i;
            }
        };
        
        this.prUpdateGroups();
    }
    
    setGroupProperties {
        |...nameProps|
        groupProperties = groupProperties ?? {()};
        
        nameProps.pairsDo {
            |name, props|
            groupProperties[name] = ( groupProperties[name] ?? {()} ).putAll(props)
        };
        
        this.prUpdateGroups();
    }
    
    prUpdateGroups {
        groupProperties = groupProperties ?? {()};
        
        envir.keysValuesDo {
            |name, cv|
            cv.md[\group] !? {
                |group|
                var groupName;
                
                if (group.isKindOf(Symbol)) {
                    groupName = group;
                    group = ();
                } {
                    groupName = group[\name].asSymbol;
                };
                
                groupProperties[groupName] = groupProperties[groupName] ?? {(name: groupName)};
                groupProperties[groupName].putAll(group);
                
                cv.md[\group] = groupProperties[groupName];
            }
        };
    }
    
    make {
        |func|
        allowCreate = true;
        
        protect {
            super.make(func);
        } {
            allowCreate = false;
        }
    }
    
    use {
        |func|
        var result;
        
        allowCreate = true;
        
        protect {
            result = super.use(func);
        } {
            allowCreate = false;
        };
        
        ^result;
    }
    
    makeGroup {
        |group, func|
        groupPrefix = groupPrefix.add(group);
        ^protect {
            this.make(func);
        } {
            groupPrefix.pop();
        }
    }
    
    useGroup {
        |group, func|
        groupPrefix = groupPrefix.add(group);
        ^protect {
            this.use(func);
        } {
            groupPrefix.pop();
        }
    }
    
    resetToDefault {
        envir.keysValueDo {
            |key, val|
            val.value = val.spec.default;
        }
    }
    
    asSynthArgs {
        |...keys|
        var args = if (groupPrefix.size > 0) {
            this.prefixCollect(groupPrefix, {|v| v })
        } {
            envir
        };
        
        if (keys.size > 0) {
            ^args.select { |v, k| keys.includes(k) };
        };
        
        ^args.asPairs
    }
    
    asSynthMapArgs {
        |...keys|
        var vals = this.asSynthArgs(*keys);
        var newVals = Array(vals.size);
        
        vals = vals.pairsDo({
            |key, val|
            newVals.add(key);
            newVals.add(val.asMap);
        });
        
        ^newVals
    }
    
    default_{
        |inDefault|
        if (inDefault.isKindOf(Class)) {
            default = { inDefault.new() }
        } {
            default = inDefault
        }
    }
    
    prMakeName {
        |name|
        ^(groupPrefix ++ [name]).join("_").asSymbol
    }
    
    at {
        |...keys|
        var control, created, key;
        
        if (keys.size == 1) {
            key = keys[0]
        } {
            key = keys.join("_").asSymbol
        };
        
        key = this.prMakeName(key);
        control = super.at(key);
        created = false;
        
        if(control.isNil && allowCreate) {
            control = default.value(key);
            control.name = key;
            control.md[\group] = control.md[\group] ?? { this.class.splitPrefix(key) };
            super.put(key, control);
            created = true;
        };
        
        this.prUpdateGroups();
        if (created) { this.changed(\controls) };
        
        ^control
    }
    
    atAll {
        |keys|
        ^keys.collect {
            |key|
            this.at(key)
        }
    }
    
    put {
        |key, value|
        var control;
        
        key = this.prMakeName(key);
        control = super.at(key);
        
        if (control.isNil || value.isNil || (control.class != value.class)) {
            if (value.notNil) {
                value.md[\group] = value.md[\group] ?? { this.class.splitPrefix(key) };
            };
            super.put(key, value);
            this.changed(\controls);
        } {
            control.setFrom(value);
        };
        
        this.prUpdateGroups();
        
        value !? {
            if (value.name.isNil or:{ value.name.asString.isEmpty }) {
                value.name = key; "setting name to %".format(key).postln;
            }
        }
    }
    
    setSpecs {
        |specs, prefix|
        protect {
            allowCreate = true;
            specs.keysValuesDo {
                |name, spec|
                name = prefix !? { "%_%".format(prefix, name).asSymbol } ?? { name };
                this.at(name).spec = spec
            };
        } {
            allowCreate = false;
        }
    }
    
    setValues {
        |envir|
        var control;
        fadeRoutine !? { fadeRoutine.stop; fadeRoutine = nil };
        
        envir.keysValuesDo {
            |key, value|
            control = super.at(key);
            control !? {
                control.value = value;
            }
        }
    }
    
    fadeValues {
        |fadeTo, time=15, rate=15, warp=\lin|
        fadeRoutine !? { fadeRoutine.stop; fadeRoutine = nil };
        
        fadeRoutine = Routine({
            var start, end;
            
            start = ();
            end = ();
            
            fadeTo.keys.do {
                |key|
                start[key] = this[key].input;
                end[key] = this[key].spec.unmap(fadeTo[key]);
            };
            
            (rate * time).do {
                |i|
                i = i.linlin(0, rate * time, 0.001, 1, warp);
                this.setInputs(blend(start, end, i));
                rate.reciprocal.wait;
            }
        });
        
        fadeRoutine.play;
    }
    
    setInputs {
        |envir|
        var control;
        
        envir.keysValuesDo {
            |key, input|
            control = super.at(key);
            control !? {
                control.input = input;
            }
        }
    }
    
    storeValues {
        |name, keys, groups|
        var string, header, doc, longestKey = 10;
        var filteredEnvir = ();
        
        envir.keysValuesDo {
            |key, value|
            if (keys.isNil && groups.isNil) {
                filteredEnvir[key] = value
            } {
                if (keys !? _.includes(key)) {
                    filteredEnvir[key] = value
                } {
                    
                    if (groups !? _.includes(ControlValueEnvir.splitPrefix(key))) {
                        filteredEnvir[key] = value
                    };
                }
            }
        };
        
        name = name ? "_";
        
        header = "// ControlValueEnvir preset: %\n".format(name);
        string = header ++ "(\n";
        
        filteredEnvir.keys.do {
            |name|
            longestKey = max(longestKey, name.asString.size + 1);
        };
        
        filteredEnvir.keysValuesDo {
            |key, val|
            string = string ++ "\t";
            string = string ++ "%:".format(key.asString).padRight(longestKey + 4);
            string = string ++ "%,\n".format(val.value.asCompileString);
        };
        
        string = string ++ ")\n";
        doc = Document(name, string);
        {
            doc.promptToSave = false;
            doc.front;
            doc.selectRange(header.size, string.size - header.size);
        }.defer(0.2);
        
        ^string;
    }
    
    mapToSynthArgs {
        |node ...keys|
        
        if (keys.size == 0) {
            keys = envir.keys;
        };
        
        // Fix for nodeproxy - we DON'T want to map to fadeTime!
        if (node.isKindOf(NodeProxy)) {
            keys.remove(\fadeTime);
            keys.remove(\gate);
        };
        
        Server.default.makeBundle(nil, {
            node.set(*this.asSynthMapArgs(*keys.asArray))
        });
    }
    
    mapToPrefix {
        |node, prefix|
        var mapArgs = [];
        
        this.prefixKeys(prefix).do {
            |key|
            mapArgs = mapArgs.add(
                this.class.removePrefix(prefix, key),
                super.at(key).asMap
            )
        }
    }
    
    connectToSynthArgs {
        |node ...keys|
        var connections = Array(envir.size);
        
        if (keys.size == 0) {
            keys = envir.keys;
        };
        
        // Fix for nodeproxy - we DON'T want to map to fadeTime!
        if (node.isKindOf(NodeProxy)) {
            keys.remove(\fadeTime);
            keys.remove(\gate);
        };
        
        keys.do {
            |name|
            connections.add(
                envir.at(name).signal(\value).connectTo(node.argSlot(name))
            )
        };
        
        connections = ConnectionList.newFrom(connections);
        connections.freeAfter(node);
        ^connections
    }
    
    addSynthArgs {
        |synthOrDef, prefix|
        var nodeId;
        allowCreate = true;
        protect {
            if (synthOrDef.isKindOf(Synth)) {
                nodeId = synthOrDef.nodeID;
                synthOrDef = synthOrDef.defName;
            };
            
            if (synthOrDef.isKindOf(Symbol)) {
                synthOrDef = synthOrDef.asSynthDesc !? _.def
            };
            
            if (synthOrDef.isKindOf(NodeProxy)) {
                prefix = "Ndef(%)".format("\\" ++ synthOrDef.key)
            };
            
            if (synthOrDef.notNil) {
                prefix = prefix ?? {
                    synthOrDef.name ++ (
                        nodeId !? { "[%]".format(nodeId) } ?? {""}
                    )
                };
                
                synthOrDef.specs.keysValuesDo {
                    |name, spec|
                    var prefixedName = "%_%".format(prefix, name).asSymbol;
                    this[prefixedName].spec = spec;
                    this[prefixedName].name = name;
                }
            } {
                "Could not make sense of argument % as a SynthDef...".format(synthOrDef).warn;
            }
        } { 
            |e|
            allowCreate = false 
        }
    }
    
    prefixKeys {
        |prefix|
        if (prefix.isNil) { ^this.nonprefixKeys };
        if (prefix.isKindOf(Array)) { prefix = prefix.join("_") };
        prefix = prefix.asString ++ "_";
        
        ^this.keys.select({
            |key|
            key.asString.beginsWith(prefix)
        })
    }
    
    nonprefixKeys {
        ^this.keys.reject({
            |key|
            key.asString.contains("_")
        })
    }
    
    prefixes {
        ^this.keys.collect(this.class.splitPrefix(_)).asSet
    }
    
    prefixDo {
        |prefix, func|
        this.prefixKeys(prefix).do {
            |key, i|
            func.value(
                this.class.removePrefix(prefix, key),
                envir.at(key),
                i
            )
        };
    }
    
    prefixCollectAs {
        |prefix, func, class|
        func = func ? { |v| v };
        ^this.prefixKeys(prefix).collectAs({
            |key, i|
            var subKey = this.class.removePrefix(prefix, key);
            
            func.value(
                envir.at(key),
                subKey,
                i
            )
        }, class);
    }
    
    prefixCollect {
        |prefix, func|
        func = func ? { |v| v };
        ^this.prefixKeys(prefix).collectAs({
            |key, i|
            var subKey = this.class.removePrefix(prefix, key);
            
            subKey -> func.value(
                envir.at(key),
                subKey,
                i
            )
        }, Event);
    }
    
    *splitPrefix {
        |key|
        key = key.asString.split($_);
        if (key.size == 1) {
            ^nil
        } {
            ^key[0..(key.size-2)].join("_").asSymbol
        }
    }
    
    *removePrefix {
        |prefix, key|
        if (prefix.isNil) { ^key };
        if (prefix.isKindOf(Array)) { prefix = prefix.join("_") };
        ^key.asString.replace(prefix ++ "_", "").asSymbol
    }
    
    // asPattern {
    //     |prefix, asValues=false|
    //     if (asMap) {
    //         if (prefix.notNil) {
    //             ^Pfunc({
    //                 |e|
    //                 this.prefixCollect(prefix, _.asMap);
    //             })
    //         } {
    //             ^Pfunc({
    //                 |e|
    //                 Event.newFrom(this.envir).collect(_.asMap);
    //             })
    //         }
    
    //     } {
    //         if (prefix.notNil) {
    //             ^Pfunc({
    //                 |e|
    //                 this.prefixCollect(prefix, _.value);
    //             })
    //         } {
    //             ^Pfunc({
    //                 |e|
    //                 Event.newFrom(this.envir).collect(_.value);
    //             })
    //         }
    //     }
    // }
    asPattern {
        |prefix, initial=(())|
        var getInitial;
        if (initial == \event) {
            getInitial = { |key| key }
        } {
            getInitial = initial[_]
        };
        
        if (prefix.notNil) {
            ^Pbind(
                *this.prefixCollectAs(prefix, {
                    |cv, key|
                    [key, cv.asPattern(getInitial.(key))]
                }, Array).flatten
            )
        } {
            ^Pbind(
                *envir.collect({
                    |cv, key|
                    cv.asPattern(getInitial.(key))
                }).asPairs
            )
        }
    }
    
    asValuePattern {
        |prefix, initial=(())|
        var getInitial;
        if (initial == \event) {
            getInitial = { |key| key }
        } {
            getInitial = initial[_]
        };
        
        if (prefix.notNil) {
            ^Pbind(
                *this.prefixCollectAs(prefix, {
                    |cv, key|
                    [key, cv.asValuePattern(getInitial.(key))]
                }, Array).flatten
            )
        } {
            ^Pbind(
                *envir.collectAs({
                    |cv, key|
                    [key, cv.asValuePattern(getInitial.(key))]
                }, Array).flatten
            )
        }
    }
    
    asStream {
        |prefix, asMap=false|
        if (asMap) {
            ^this.asPattern(prefix).asStream   
        } {
            ^this.asValuePattern(prefix).asStream   
        }
    }
    
    keys { ^envir.keys }
    values { ^envir.values }
    
    addMetadata {
        |...args|
        var isKeyValPairs = args.size.even && args[0].isKindOf(Symbol);
        
        if (isKeyValPairs) {
            ^this.addMetadata(Event.newFrom(args))
        } {
            args.do {
                |envir|
                this.do {
                    |cv|
                    cv.metadata.putAll(envir)
                }
            }
        }
    }
    
}

ControlValueEnvirStates : Singleton {
    var <states;
    var cve;
    var <storePath, fileConn;
    var <>include, <>exclude;
    
    init {
        cve = name; // we use the cve as our key
        states = ();
        include = { true };
        exclude = { false };
        
        this.signal(\states).connectTo({
            storePath !? { this.saveToFile(storePath) }
        })
    }
    
    storePath_{
        |inPath|
        if (storePath != inPath) {
            if (storePath.notNil) {
                this.saveToFile(storePath);
            };
            
            storePath = inPath;
            
            if (storePath.notNil and: { File.exists(storePath) }) {
                this.loadFromFile(storePath);
            }
        }
    }
    
    states_{
        |newStates|
        if (states != newStates) {
            states = newStates;
            this.changed(\states);
        }
    }
    
    saveState {
        |name, include=true, exclude=false|
        var state = ();
        
        include = include || this.include;
        exclude = exclude || this.exclude;
        
        name = name.asSymbol;
        
        cve.envir.keysValuesDo {
            |name, cv|
            if (include.(name, cv) and: { exclude.(name, cv).not }) {
                state[name] = cv.value
            }
        };
        
        states[name] = state;
        
        this.changed(\states);
    }
    
    loadState {
        |name|
        var state;
        
        name = name.asSymbol;
        states[name] !? {
            |state|
            state.keysValuesDo {
                |key, value|
                cve[key].value = value;
            }
        }
    }
    
    removeState {
        |name|
        name = name.asSymbol;
        states[name] = nil;
    }
    
    serializeDict {
        |dict, indent=4, pad=24|
        indent = (" " ! indent).join;
        
        ^"(\n%\n)".format(
            dict
                .asAssociations
                .sort({
                    |a, b|
                    a.key > b.key
                })
                .collect({
                    |assoc|
                    "%%,".format(
                        (indent ++ assoc.key.asString ++ ":").padRight(pad),
                        assoc.value
                    )
                })
                .join("\n")
        )
    }
    
    serialize {
        var str = "(\n%\n\n(\n%\n)\n\n);\n";
        var divider ="\n" ++ ("/" ! 300).join ++ "\n";
        
        str = str.format(
            
            "// Saved states for ControlValueEnvir[%]".format(cve.name),
            
            (states
                .asAssociations
                .sort({
                    |a, b|
                    a.key < b.key
                })
                .collect({
                    |assoc|
                    divider
                        ++ "%: %,".format(
                            assoc.key,
                            this.serializeDict(assoc.value)
                        )
                        ++ divider
                })
                .join("\n")
            )
        );
        
        ^str;
    }
    
    saveToFile {
        |path|
        "Serializing to file %".format(path).postln;
        File(path, "w").write(this.serialize).close();
    }
    
    loadFromFile {
        |path|
        "Deserializing from file %".format(path).postln;
        this.deserialize(File.readAllString(path));
    }
    
    deserialize {
        |string|
        states = string.interpret
    }
}



