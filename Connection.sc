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

	connectionCleared {
		this.clear
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

	trace {
		|shouldTrace=true|
		list.do(_.trace(shouldTrace));
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
		|delta=0, clock=(AppClock), force=false|
		list.do(_.defer(delta, clock, force))
	}

	collapse {
		|clock, force, delay|
		list.do(_.collapse(clock, force, delay))
	}
}

Connection {
	classvar collectionStack;
	classvar <tracing, <>traceAll=false;

	var <object, <dependant;
	var connected = false;
	var <traceConnection;

	*initClass {
		tracing = List();
	}

	*traceWith {
		|func|
		var collected, wasTracingAll;
		wasTracingAll = traceAll;
		traceAll = true;

		protect({
			collected = Connection.collect(func);
		}, {
			if (wasTracingAll.not) {
				collected.list.do(_.trace(false));
			};
			traceAll = wasTracingAll;
		})
	}

	*collect {
		|func|
		this.prBeforeCollect();
		protect {
			func.value()
		} {
			^this.prAfterCollect();
		}
	}

	*prBeforeCollect {
		collectionStack = collectionStack.add(List(20));
	}

	*prAfterCollect {
		var popped = collectionStack.pop();
		if (collectionStack.size > 1) {
			collectionStack.last.addAll(popped);
		};
		^ConnectionList(popped);
	}

	*basicNew {
		|object, dependant, connected|
		^super.newCopyArgs(object, dependant, connected).trace(traceAll).prCollect()
	}

	*new {
		|object, dependant, autoConnect=true|
		^super.newCopyArgs(object, dependant).connected_(autoConnect).trace(traceAll).prCollect()
	}

	*untraceAll {
		tracing.copy.do(_.trace(false));
	}

	prCollect {
		if (collectionStack.size > 0) {
			collectionStack.last.add(this);
		}
	}

	connected {
		traceConnection.notNil.if {
			^traceConnection.connected
		} {
			^object.dependants.includes(dependant);
		}
	}

	connected_{
		|connect|
		if (traceConnection.isNil) {
			if (connect != this.connected) {
				connected = connect;
				if (connect) {
					object.addDependant(dependant);
				} {
					object.removeDependant(dependant);
				}
			}
		} {
			traceConnection.connected = connect;
		}
	}

	connect {
		this.connected_(true)
	}

	disconnect {
		this.connected_(false)
	}

	connectionCleared {
		this.clear();
	}

	clear {
		this.disconnect();
		object.connectionCleared(this);
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

	onTrace {
		|obj, what ...values|
		var from, to, connectedSym;
		from = object.isKindOf(Connection).if({ object.dependant }, { object });
		to = dependant.isKindOf(UpdateTracer).if({ dependant.wrappedObject }, { dependant });
		connectedSym = this.connected.if("⋯", "⋰");

		"% %.signal(%) → %\t =[%]".format(
			connectedSym++connectedSym,
			from.connectionTraceString(what),
			"\\" ++ what,
			to.connectionTraceString(what),
			(values.collect(_.asCompileString)).join(","),
		).postln
	}

	trace {
		|shouldTrace=true|
		if (shouldTrace) {
			traceConnection ?? {
				traceConnection = UpdateTracer(object, dependant, this);
				object.removeDependant(dependant);
				object.addDependant(traceConnection);
				tracing.add(this);
			}
		} {
			traceConnection !? {
				var tempTrace = traceConnection;
				traceConnection = nil;
				object.removeDependant(tempTrace);
				this.connected = tempTrace.connected;
				tracing.remove(this);
			}
		}
	}

	traceWith {
		|func|
		var wasTracing = traceConnection.notNil;
		this.trace(true);
		protect(func, {
			this.trace(wasTracing);
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
		var wasTracing = traceConnection.notNil;
		this.trace(false);

		this.disconnectWith({
			object = object.connectTo(newDependant);
		});
		this.trace(wasTracing);
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
	classvar funcs, onCloseFunc;

	*initClass {
		funcs = MultiLevelIdentityDictionary();
		onCloseFunc = {
			|view, actionName, func|
			ViewValueUpdater.disable(view, actionName, func);
		};
	}

	*actionFunc {
		|propertyName\value, signalName=\value|
		var func = funcs.at(propertyName, signalName);
		if (func.isNil) {
			func = "{ |view ...args| view.changed('%', view.%) }".format(signalName, propertyName).interpret;
			funcs.put(propertyName, signalName, func);
		};
		^func;
	}

	*isConnected {
		|view, actionName, actionFunc|
		var isConnected = false;
		isConnected == isConnected || (view.perform(actionName) == actionFunc);
		if (view.perform(actionName).isKindOf(FunctionList)) {
			isConnected = isConnected || view.perform(actionName).array.includes(actionFunc);
		};
		^isConnected;
	}

	*enable {
		|view, actionName=\action, propertyName=\value, signalName=\value|
		var func = this.actionFunc(propertyName, signalName);
		if (this.isConnected(view, actionName, func).not) {
			view.perform(actionName.asSetter, view.perform(actionName).addFunc(func));
			view.onClose = view.onClose.addFunc(onCloseFunc.value(_, actionName, func));
		}
	}

	*disable {
		|view, actionName, func|
		view.perform(actionName.asSetter, view.perform(actionName).removeFunc(func));
	}
}

UpdateForwarder {
	var <dependants;

	*new {
		^super.new.prInitDependants;
	}

	prInitDependants{
		dependants = IdentitySet();
	}

	changed { arg what ... moreArgs;
		dependants.do({ arg item;
			item.update(this, what, *moreArgs);
		});
	}

	addDependant { arg dependant;
		dependants.add(dependant);
	}

	removeDependant { arg dependant;
		dependants.remove(dependant);
	}

	release {
		this.releaseDependants;
	}

	releaseDependants {
		dependants.clear();
	}

	update {
		|object, what ...args|
		dependants.do {
			|item|
			item.update(object, what, *args);
		}
	}
}

UpdateTracer {
	var <upstream, <wrappedObject, <connection;
	var <>connected;

	*new {
		|upstream, wrappedObject, connection|
		^super.newCopyArgs(upstream, wrappedObject, connection).init
	}

	init {
		connected = upstream.dependants.includes(wrappedObject);
	}

	update {
		|object, what ...args|
		connection.onTrace(object, what, *args);
		if (connected) {
			wrappedObject.update(object, what, *args);
		}
	}

	addDependant {
		|dependant|
		wrappedObject.addDependant(dependant);
	}

	removeDependant {
		|dependant|
		wrappedObject.removeDependant(dependant);
	}

	release {
		wrappedObject.release();
	}

	releaseDependants {
		wrappedObject.releaseDependants();
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
	var <>key;

	*new {
		|key|
		var func = "{ |obj, inKey| % == inKey }".format("\\" ++ key).interpret;
		^super.new(func).key_(key);
	}

	connectionTraceString {
		^"%(%)".format(this.class, "\\" ++ key)
	}
}

UpdateDispatcher {
	classvar <dispatcherDict;

	var dispatchTable;
	var connection;

	*initClass {
		dispatcherDict = IdentityDictionary();
	}

	*new {
		|object|
		^dispatcherDict.atFail(object, {
			var newObj = super.new.init(object);
			dispatcherDict[object] = newObj;
			newObj;
		})
	}

	init {
		|object|
		connection = object.connectTo(this);
		dispatchTable = IdentityDictionary();
	}

	at {
		|key|
		^dispatchTable.atFail(key, {
			var dep = UpdateForwarder();
			dispatchTable[key] = dep;
			dep;
		})
	}

	dependants {
		^(super.dependants ++ dispatchTable.values);
	}

	update {
		|obj, what ...args|
		dispatchTable[what] !? {
			|forwarder|
			forwarder.update(obj, what, *args);
		}
	}

	connectionTraceString {
		|what|
		^dispatchTable[what].connectionTraceString ?? { "%(%) - no target".format(this.class, this.identityHash) };
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

	*makeUpdateFunc {
		|reciever, methodName, argOrder|
		var argString, callString;
		var possibleArgs = ['object', 'changed', '*args', 'args', 'value'];

		if (methodName.isKindOf(String) && argOrder.isEmpty) {
			// Should be of the form e.g. "someMethod(value, arg[0])"
			callString = methodName;
			methodName = methodName.split($()[0].asSymbol; // guess the method name - used later for validation
		} {
			argOrder.do {
				|a|
				if (a.isInteger.not && possibleArgs.includes(a).not) {
					Error("Can't handle arg '%' - must be one of: %.".format(a, possibleArgs.join(", "))).throw
				}
			};

			if (argOrder.isEmpty) {
				argOrder = ['object', 'changed', '*args'];
			};

			argString = argOrder.collect({
				|a|
				if (a.isInteger) {
					"args[%]".format(a)
				} {
					a.asString
				}
			}).join(", ");
			callString = "%(%)".format(methodName, argString);
		};

		if (reciever.respondsTo(methodName).not && reciever.tryPerform(\know).asBoolean.not) {
			Exception("Object of type % doesn't respond to %.".format(reciever.class, methodName)).throw;
		};

		^"{ |reciever, object, changed, args| var value = args[0]; reciever.% }".format(callString).interpret;
	}

	update {
		|object, changed ...args|
		updateFunc.value(reciever, object, changed, args);
	}

	connectionTraceString {
		|what|
		^"%(%(%).%)".format(this.class, reciever.class, reciever.identityHash, methodName)
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

DeferredUpdater : UpdateForwarder {
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
		if ((thisThread.clock == clock) && force.not) {
			super.update(object, what, *args);
		} {
			clock.sched(delta, {
				super.update(object, what, *args);
			})
		}
	}
}

OneShotUpdater : UpdateForwarder {
	var <>connection;

	*new {
		|connection|
		^super.new.connection_(connection)
	}

	update {
		|object, what ...args|
		protect {
			super.update(object, what, *args);
		} {
			connection.disconnect();
		}
	}
}

CollapsedUpdater : UpdateForwarder {
	var clock, force, delta;
	var deferredUpdate;
	var holdUpdates=false;

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

	deferIfNeeded {
		|func|
		if ((thisThread.clock == clock) && force.not) {
			func.value
		} {
			clock.sched(0, func);
		}
	}

	update {
		|object, what ...args|
		if (holdUpdates) {
			deferredUpdate = [object, what, args];
		} {
			holdUpdates = true;

			this.deferIfNeeded {
				super.update(object, what, *args);
			};

			clock.sched(delta, {
				holdUpdates = false;
				if (deferredUpdate.notNil) {
					var tmpdeferredUpdate = deferredUpdate;
					deferredUpdate = nil;
					this.update(tmpdeferredUpdate[0], tmpdeferredUpdate[1], *tmpdeferredUpdate[2]);
				};
			})
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

AbstractControlValue {
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

NumericControlValue : AbstractControlValue {
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
	classvar objDict;

	*initClass {
		objKeyDict = IdentityDictionary();
		objDict = IdentityDictionary();
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
		|...dependants|
		var autoConnect = if (dependants.last.isKindOf(Boolean)) { dependants.pop() } { true };
		if (dependants.size == 1) {
			^Connection(this, dependants[0], autoConnect);
		} {
			^CollectionList(dependants.collect {
				|dependant|
				Connection(this, dependant, autoConnect)
			})
		}
	}

	signal {
		|keyOrFunc|
		if (keyOrFunc.isNil) {
			^this
		} {
			if (keyOrFunc.isKindOf(Symbol)) {
				^UpdateDispatcher(this).at(keyOrFunc);
			} {
				^this.connectTo(UpdateFilter(keyOrFunc));
			}
		}
	}

	connectionTraceString {
		^"%(%)".format(this.class, this.identityHash)
	}

	connectionCleared {}
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


