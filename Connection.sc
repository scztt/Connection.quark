ConnectionList {
	var <list;

	*newFrom {
		|connectionList|
		^this.newCopyArgs(connectionList.list);
	}

	*new {
		|list|
		^super.newCopyArgs((list ?? List()).asList)
	}

	connected_{
		|connect|
		list.do(_.connected_(connect));
	}

	connect {
		list.do(_.connect);
	}

	disconnect {
		list.do(_.disconnect);
	}

	clear {
		list.do(_.clear);
		list = nil;
	}

	disconnectWith {
		|func|
		var wasConnected = list.filter(_.connected);

		this.disconnect();

		^func.protect({
			wasConnected.do(_.connect)
		});
	}

	dependants {
		^list.collect({ |o| o.dependants.asList }).flatten;
	}

	addDependant {
		|dep|
		list.do(_.addDependant(dep));
	}

	removeDependant {
		|dep|
		list.do(_.removeDependant(dep));
	}

	releaseDependants {
		list.do(_.releaseDependants());
	}

	connectTo {
		|nextDependant, autoConnect=true|
		^Connection(this, nextDependant, autoConnect);
	}

	chain {
		|newDependant|
		list.do(_.chain(newDependant));
	}

	filter {
		|filter|
		list.do(_.filter(filter));
	}

	transform {
		|func|
		list.do(_.transform(func))
	}

	defer {
		|clock, force, delay|
		list.do(_.defer(clock, force, delay))
	}

	collapse {
		|clock, force, delay|
		list.do(_.collapse(clock, force, delay))
	}
}

Connection {
	var <object, <dependant;
	var connected = false;

	*basicNew {
		|object, dependant, connected|
		^super.newCopyArgs(object, dependant, connected)
	}

	*new {
		|object, dependant, autoConnect=true|
		^super.newCopyArgs(object, dependant).connected_(autoConnect);
	}

	connected {
		^object.dependants.includes(dependant)
	}

	connected_{
		|connect|
		if (connect != this.connected) {
			connected = connect;
			if (connect) {
				object.addDependant(dependant);
			} {
				object.removeDependant(dependant);
			}
		}
	}

	connect {
		this.connected_(true)
	}

	disconnect {
		this.connected_(false)
	}

	clear {
		this.disconnect();
		object = dependant = nil;
	}

	disconnectWith {
		|func|
		var wasConnected = this.connected;

		this.disconnect();

		^func.protect({
			if (wasConnected) {
				this.connect();
			}
		});
	}

	dependants {
		^dependant.dependants
	}

	addDependant {
		|dep|
		// if (dependant.dependants.isEmpty) {
		// 	this.connect();
		// };

		dependant.addDependant(dep);
	}

	removeDependant {
		|dep|
		dependant.removeDependant(dep);

		// if (dependant.dependants.isEmpty) {
		// 	this.disconnect();
		// }
	}

	releaseDependants {
		dependant.releaseDependants();
		this.disconnect();
	}

	connectTo {
		|nextDependant|
		^Connection(this, nextDependant);
	}

	chain {
		|newDependant|
		var oldObject, connection;

		this.disconnectWith({
			object = object.connectTo(newDependant);
		})
	}

	filter {
		|filter|
		if (filter.isKindOf(Symbol)) {
			this.chain(UpdateKeyFilter(filter))
		} {
			this.chain(UpdateFilter(filter))
		}
	}

	transform {
		|func|
		this.chain(UpdateTransform(func))
	}

	defer {
		|delta=0, clock=(AppClock), force=false|
		this.chain(DeferredUpdater(delta, clock, force));
	}

	collapse {
		|delta=0, clock=(AppClock), force=true|
		this.chain(CollapsedUpdater(delta, clock, force))
	}
}

ViewValueUpdater {
	classvar valueSignalFunc, onCloseFunc;

	*initClass {
		valueSignalFunc = {
			|view|
			view.changed(\value, view.value);
		};
		onCloseFunc = {
			|view|
			ViewValueUpdater.disable(view);
		};
	}

	*isConnected {
		|view|
		var isConnected = false;
		isConnected == isConnected || (view.action == valueSignalFunc);
		if (view.action.isKindOf(FunctionList)) {
			isConnected = isConnected || view.action.array.includes(valueSignalFunc);
		};
		^isConnected;
	}

	*enable {
		|view|
		if (this.isConnected(view).not) {
			view.action = view.action.addFunc(valueSignalFunc);
			view.onClose = view.onClose.add(onCloseFunc)
		}
	}

	*disable {
		|view|
		view.action = view.action.removeFunc(valueSignalFunc);
	}
}

ConnectionManager {
	var connections;

	*new {
		^super.new.init;
	}

	init {
		connections = List();
	}

	connect {
		|from, to|
		connections.add(Connection(from, to));
	}

	disconnect {
		|object, dependant|
		var found;
		found = connections.select {
			|conn|
			(object.isNil || (object == conn.object))
			&& (dependant.isNil || (dependant == conn.dependant))
		};

		found.do({
			|conn|
			connections.remove(conn.disconnect());
		});
	}

	disconnectAll {
		connections.do(_.disconnect());
		connections.clear();
	}
}

ForwardingSlot {
	update {
		|object, what ...args|
		dependantsDictionary.at(this).copy.do({ arg item;
			item.update(object, what, *args);
		});
	}
}

UpdateChannel : Singleton {
	update {
		|object, what ...args|
		this.changed(what, *args)
	}
}

UpdateBroadcaster : Singleton {
	// simply rebroadcast
	update {
		|object, what ...args|
		dependantsDictionary.at(this).copy.do({ arg item;
			item.update(object, what, *args);
		});
	}
}

UpdateFilter {
	var <func;

	*new {
		|func|
		^super.newCopyArgs(func)
	}

	update {
		|object, what ...args|
		if (func.value(object, what, *args)) {
			dependantsDictionary.at(this).copy.do({ arg item;
				item.update(object, what, *args);
			});
		}
	}
}

UpdateTransform {
	var <func;

	*new {
		|func|
		^super.newCopyArgs(func)
	}

	update {
		|object, what ...args|
		var argsArray = func.value(object, what, *args);
		if (argsArray.notNil) {
			argsArray = argsArray[0..1] ++ argsArray[2];
			dependantsDictionary.at(this).copy.do({ arg item;
				item.update(*argsArray);
			});
		}
	}
}

ConnectionMap : ConnectionList {
	*new {
		|...args|
		var dict = IdentityDictionary.newFrom(args);
		var transformer = UpdateTransform({
			|obj, changed ...args|
			var newKey = dict[obj];
			if (newKey.notNil) {
				[obj, newKey, args];
			} {
				nil;
			}
		});

		^super.newFrom(dict.keys.connectAll(transformer))
	}
}

UpdateKeyFilter : UpdateFilter {
	*new {
		|key|
		var func = "{ |obj, inKey| % == inKey }".format("\\" ++ key).interpret;
		^super.new(func);
	}
}

MethodSlot {
	var <updateFunc, <reciever, <methodName;

	*new {
		|obj, method ...argOrder|
		^super.new.init(obj, method, argOrder)
	}

	init {
		|inObject, inMethodName, argOrder|
		reciever = inObject;
		methodName = inMethodName;
		updateFunc = MethodSlot.makeUpdateFunc(reciever, methodName, argOrder);
	}

	*parseMethodSignature {
		|method|
		var args=[];

		if (method.isKindOf(String)) {
			var match = method.findRegexp("(\\w+)\\((.*)\\)");
			if (match.notEmpty) {
				method = match[1][1].asSymbol;
				args = match[2][1].split(",").collect(_.trim).collect(_.asSymbol);
			} {
				method = method.asSymbol;
			}
		};

		^[method, args]
	}

	*makeUpdateFunc {
		|reciever, methodName, argOrder|
		var inArgString, argString;
		var method, numAdditionalArgs;
		var updateFunc;
		var possibleArgs = ['obj', 'changed', '*args', 'args', 'value'];

		if (argOrder.size == 0) {
			#methodName, argOrder = this.parseMethodSignature(methodName);
		};

		if (reciever.respondsTo(methodName).not && reciever.tryPerform(\know).asBoolean.not) {
			Exception("Object of type % doesn't respond to %.".format(reciever.class, methodName)).throw;
		};

		argOrder.do {
			|a|
			if (a.isInteger.not) {
				if (possibleArgs.includes(a).not) {
					Error("Can't handle arg '%' - must be one of: %.".format(a, possibleArgs.join(", "))).throw
				}
			}
		};

		if (argOrder.isEmpty) {
			argOrder = ['obj', 'changed', '*args'];
		};

		argString = argOrder.collect({
			|a|
			if (a.isInteger) {
				"args[%]".format(a)
			} {
				if (a == \value) {
					"args[0]"
				} {
					a.asString
				}
			}
		}).join(", ");

		^updateFunc = "{ |reciever, obj, changed, args| reciever.%(%) }".format(methodName, argString).interpret;
	}

	update {
		|obj, changed ...args|
		updateFunc.value(reciever, obj, changed, args);
	}
}

MultiMethodSlot {
	var <object, <mapFunction;

	*new {
		|obj ...argsMap|
		^super.newCopyArgs(obj).init(argsMap)
	}

	init {
		|argsMap|
		if (argsMap.size == 0) {
			mapFunction = {|k| k};
		} {
			if ((argsMap.size == 1) && argsMap[0].isFunction) {
				mapFunction = argsMap[0]
			} {
				argsMap = argsMap.copy.asDict;
				mapFunction = argsMap[_];
			}
		}
	}

	update {
		|obj, what ...args|
		var method = mapFunction.value(what);
		if (method.notNil) {
			object.perform(method, obj, what, *args);
		}
	}
}

ValueSlot : MethodSlot {
	*new {
		|obj, setter=\value_|
		^super.new(obj, setter, \value)
	}
}

FunctionSlot : MethodSlot {
	*new {
		|obj ...argOrder|
		^super.new(obj, \value, *argOrder)
	}
}

SynthArgSlot {
	var <synth, <>argName, synthConn;

	*new {
		|synth, argName|
		^super.newCopyArgs(synth, argName).init
	}

	init {
		synth.register;
		synthConn = synth.connectTo(this.methodSlot(\free)).filter(\n_end);
	}

	free {
		synthConn.disconnect().clear();
		synth = argName = synthConn =nil;
	}

	update {
		|obj, what, value|
		if (synth.notNil) {
			synth.set(argName, value);
		}
	}
}

SynthMultiArgSlot {
	var <synth, <mapFunction, synthConn;

	*new {
		|synth ...argsMap|
		^super.newCopyArgs(synth).init(argsMap)
	}

	init {
		|argsMap|
		synth.register;
		synthConn = synth.connectTo(this.methodSlot(\free)).filter(\n_end);

		if (argsMap.size == 0) {
			mapFunction = {|k| k};
		} {
			if ((argsMap.size == 1) && argsMap[0].isFunction) {
				mapFunction = argsMap[0]
			} {
				argsMap = argsMap.copy.asDict;
				mapFunction = argsMap[_];
			}
		}
	}

	free {
		synthConn.disconnect().clear();
		synth = mapFunction = synthConn =nil;
	}

	update {
		|obj, what, value|
		var argName = mapFunction.value(what);
		if (argName.notNil) {
			synth.set(argName, value);
		}
	}
}

SynthValueMapSlot : SynthMultiArgSlot {
	update {
		|obj, what, value|
		if (what == \value) {
			var argName = mapFunction.value(obj);
			if (argName.notNil) {
				synth.set(argName, value);
			}
		}
	}
}

DeferredUpdater : ForwardingSlot {
	var clock, force, delta;

	*new {
		|delta=0, clock=(AppClock), force=true|
		^super.new.init(delta, clock, force)
	}

	init {
		|inDelta, inClock, inForce|
		clock = inClock;
		force = inForce;
		delta = inDelta;
	}

	update {
		|object, what ...args|
		if ((thisThread.clock == clock) || force.not) {
			super.update(object, what, *args);
		} {
			clock.sched(delta, {
				super.update(object, what, *args);
			})
		}
	}
}

CollapsedUpdater : DeferredUpdater {
	var deferredCall;

	update {
		|object, what ...args|
		if ((thisThread.clock == clock) || force.not) {
			deferredCall = nil;
			super.update(object, what, *args);
		} {
			if (deferredCall.isNil) {
				clock.sched(delta, {
					var tmpDeferredCall = deferredCall;
					deferredCall = nil;
					super.update(tmpDeferredCall[0], tmpDeferredCall[1], *tmpDeferredCall[2]);
				})
			};

			deferredCall = [object, what, args];
		}
	}
}

PeriodicUpdater {
	var <object, <method;
	var <freq, <>name;
	var <process, <lastVal;

	*new {
		|object, method=\value, freq=0.1|
		^super.newCopyArgs(object, method).freq_(freq).name_(method);
	}

	freq_{
		|inFreq|
		freq = inFreq;
		process.stop();
		process = SkipJack(this.pull(_), freq, name:"PeriodicUpdater_" ++ this.identityHash.asString);
	}

	start {
		process.start();
	}

	stop {
		process.stop();
	}

	pull {
		var val = object.perform(method);
		if (val != lastVal) {
			lastVal = val;
			this.changed(\value, val)
		};
	}
}

BusUpdater : PeriodicUpdater {
	*new {
		|bus, freq=0.1|
		^super.new(bus, \getSynchronous, freq);
	}
}

ControlValue {
	classvar <>defaultValue, <>defaultSpec;
	var <value, <spec;

	*new {
		|initialValue, spec|
		^super.new.init(initialValue, spec);
	}

	init {
		|initialValue, inSpec|
		value = initialValue ?? defaultValue.copy;
		spec = inSpec ?? defaultSpec.copy;
	}

	value_{
		|inVal|

		inVal = spec.constrain(inVal);

		if (value != inVal) {
			value = inVal;
			this.changed(\value, value);
		}
	}

	input_{
		|inVal|
		this.value_(spec.map(inVal));
	}

	input {
		^spec.unmap(value);
	}

	spec_{
		|inSpec|

		if (inSpec != spec) {
			spec = inSpec;
			this.changed(\spec, spec);
			this.value = value;
		}
	}
}

NumericControlValue : ControlValue {
	*initClass {
		Class.initClassTree(Spec);

		defaultValue = 0;
		defaultSpec = \unipolar.asSpec;
	}
}

MIDIControlValue : NumericControlValue {
	var midiFunc, func, isOwned=false;

	cc {
		| ccNumOrFunc, chan, srcID, argTemplate, dispatcher |
		func = func ? {
			|val|
			this.input = val / 127.0;
		};

		this.clearMIDIFunc();

		if (ccNumOrFunc.notNil) {
			if (ccNumOrFunc.isKindOf(MIDIFunc)) {
				isOwned = false;
				midiFunc = ccNumOrFunc;
				midiFunc.add(func);
			} {
				isOwned = true;
				midiFunc = MIDIFunc.cc(func, ccNumOrFunc, chan, srcID, argTemplate, dispatcher)
			}
		}
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

GlobalConnections {
	classvar objKeyDict;

	*initClass {
		objKeyDict = IdentityDictionary();
	}

	*forObjectKey {
		|obj, key, failFunc|
		var objDict;

		objDict = objKeyDict.atFail(obj, {
			var new = IdentityDictionary();
			objKeyDict[obj] = new;
			new;
		});

		^objDict.atFail(key, {
			var connection = failFunc.value();
			objDict[key] = connection;
			connection;
		});
	}
}

+Object {
	valueSlot {
		|setter=\value_|
		^ValueSlot(this, setter)
	}

	methodSlot {
		|method ...argOrder|
		^MethodSlot(this, method, *argOrder)
	}

	multiSlot {
		|...map|
		^MultiMethodSlot(this, *map);
	}

	connectTo {
		|dependant, autoConnect=true|
		^Connection(this, dependant, autoConnect);
	}

	addConnection {
		|dep|
		^this.connectTo(dep)
	}

	signal {
		|keyOrFunc|
		if (keyOrFunc.isNil) {
			^this
		} {
			^GlobalConnections.forObjectKey(this, keyOrFunc, {
				if (keyOrFunc.isKindOf(Symbol)) {
					this.connectTo(UpdateKeyFilter(keyOrFunc));
				} {
					this.connectTo(UpdateFilter(keyOrFunc));
				}
			}).connect()
		}
	}
}

+Node {
	argSlot {
		|argName|
		^SynthArgSlot(this, argName)
	}

	multiArgSlot {
		|...argMap|
		^SynthMultiArgSlot(this, *argMap);
	}

	connectValues {
		|...argMap|
		var slot = SynthValueMapSlot(this, *argMap);
		var list = List();
		argMap.pairsDo({
			|source|
			list.add(source.connectTo(slot));
		});
		^ConnectionList(list);
	}
}

+Collection {
	connectAll {
		|dependant|
		^ConnectionList(this.collect(_.connectTo(dependant)))
	}

	connectMap {
		|dependant|
		var pairs = this.asPairs.clump(2).collect(_.reverse).flatten;
		^ConnectionMap(*pairs).connectTo(dependant);
	}
}

+View {
	updateOnAction {
		|should=true|
		if (should) {
			ViewValueUpdater.enable(this);
		} {
			ViewValueUpdater.disable(this);
		}
	}
}


