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

	*makeWith {
		|func|
		Connection.prBeforeCollect();
		protect {
			func.value()
		} {
			^Connection.prAfterCollect();
		}
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

	connectionFreed {
		this.free;
	}

	free {
		list.do(_.free);
		list = nil;
	}

	disconnectWith {
		|func|
		var wasConnected = list.select(_.connected);

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
	classvar <collectionStack;
	classvar <tracing, <>traceAll=false;

	var <object, <dependant;

	var connected = false;
	var <traceConnection;

	*initClass {
		Class.initClassTree(MethodSlot);
		tracing = List();
	}

	*findReachableConnections {
		// This should operate as a *loose* leak checker. It visits known root-level objects that could contain
		// connections or temporary connection-related objects, and collects them. If connections have been properly
		// cleaned up, this should return an empty list. We don't bother drilling down into Connection-related
		// objects, since we basically only care if this list is empty or not.
		var foundObjects = List[];
		var toIterate = List();
		var hasIterated = IdentitySet();
		var itemTypes = [ViewActionUpdater, UpdateForwarder, UpdateTracer, UpdateChannel, UpdateBroadcaster, UpdateFilter, UpdateTransform, UpdateDispatcher, MethodSlot, SynthArgSlot, SynthMultiArgSlot, PeriodicUpdater];

		toIterate.addAll([
			Object.dependantsDictionary,
			Connection.tracing, Connection.collectionStack,
			UpdateDispatcher.dispatcherDict.keys
		].flatten);

		while { toIterate.notEmpty } {
			var iter = toIterate.pop();
			iter !? {
				toIterate.size.postln;
				0.0001.yield;
				hasIterated.add(iter);

				if (iter.isKindOf(Collection)) {
					var coll = iter;
					if (iter.isKindOf(Dictionary)) {
						coll = coll.keys.asArray ++ coll.values.asArray
					};
					coll.do {
						|item|
						if (hasIterated.includes(item).not) {
							toIterate.add(item);
						}
					}
				} {
					if (itemTypes.any({ |c| iter.isKindOf(c) })) {
						foundObjects.add(iter);
					}
				}
			}
		};

		^foundObjects
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

	*prBeforeCollect {
		collectionStack = collectionStack.add(List(20));
	}

	*prAfterCollect {
		^ConnectionList(collectionStack.pop());
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
			^object.dependants !? { |d| d.includes(dependant) } ?? false;
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

	connectionFreed {
		this.free();
	}

	free {
		this.trace(false);
		this.disconnect();
		object.connectionFreed(this);
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
			from.connectionTraceString(obj, what),
			"\\" ++ what,
			to.connectionTraceString(obj, what),
			(values.collect(_.asCompileString)).join(","),
		).postln
	}

	trace {
		|shouldTrace=true|
		if (shouldTrace) {
			traceConnection ?? {
				traceConnection = UpdateTracer(object, dependant, this);
				object.addDependant(traceConnection);
				object.removeDependant(dependant);
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
		if (dependant.dependants.size == 0) {
			this.connect();
		};

		dependant.addDependant(dep);
	}

	removeDependant {
		|dep|
		dependant.removeDependant(dep);

		if (dependant.dependants.size == 0) {
			this.disconnect();
		}
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
		var newConnection, wasTracing = traceConnection.notNil;

		// We want to insert newDependant in between our current object and dependant.
		// I.e.: this.object -> newDependant -> this.dependant
		// The current (this) connection will represent the [newDependant -> this.dependant]
		// portion, and we construct and return a new connection for [this.object -> newDependant].
		this.trace(false);

		newConnection = object.connectTo(newDependant);
		this.disconnect();
		object = newConnection;
		this.connect();

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

	oneShot {
		|shouldFree=false|
		this.chain(OneShotUpdater(this, shouldFree));
	}
}
