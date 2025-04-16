UpdateForwarder {
    var 		<dependants;
    var 		<namedDependants;
    
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
        if (dependants.isNil) {
            dependants = IdentitySet();
            this.onDependantsNotEmpty
        };
        
        dependants.add(dependant);
        this.onDependantAdded(dependant);
    }
    
    removeDependant { arg dependant;
        dependants.remove(dependant);
        this.onDependantRemoved(dependant);
        
        if (dependants.size == 0) {
            dependants = nil;
            this.onDependantsEmpty;
        };
    }
    
    release {
        this.releaseDependants();
    }
    
    releaseDependants {
        dependants.clear();
        this.onDependantsEmpty();
    }
    
    onDependantsEmpty {}
    
    onDependantsNotEmpty {}
    
    onDependantAdded {}
    
    onDependantRemoved {}
    
    update {
        |object, what ...args|
        dependants.do {
            |item|
            item.update(object, what, *args);
        }
    }
    
    // chaining forwarders
    chain {
        |class ...args|
        var newForwarder = class.new(*args);
        this.connectTo(newForwarder);
        ^newForwarder;
    }
    
    filter {
        |func|
        ^this.chain(UpdateFilter, func);
    }
    
    valueFilter {
        |func|
        ^this.filter({ |obj, what ...vals| func.(*vals) });
    }
    
    transform {
        |func|
        ^this.chain(UpdateTransform, func);
    }
    
    defer {
        |delta=0, clock=(AppClock), force=true|
        ^this.chain(DeferredUpdater, delta, clock, force);
    }
    
    collapse {
        |delta=0, clock=(AppClock), force=true|
        ^this.chain(CollapsedUpdater, delta, clock, force);
    }
    
    oneShot {
        |shouldFree=false|
        var newUpdater = OneShotUpdater(nil, shouldFree);
        newUpdater.connection = (this.connectTo(newUpdater));
        ^newUpdater;
    }
    
    addDoAfter {
        |func|
        this.oneShot(true).connectTo(func);
    }
}

UpdateFilter : UpdateForwarder {
    var <>func;
    
    *new {
        |func|
        ^super.new.func_(func)
    }
    
    update {
        |object, what ...args|
        if (func.value(object, what, *args)) {
            super.update(object, what, *args);
        }
    }
}

UpdateTransform : UpdateForwarder {
    var <>func;
    
    *new {
        |func|
        ^super.new.func_(func)
    }
    
    update {
        |object, what ...args|
        var argsArray = func.value(object, what, *args);
        if (argsArray.notNil) {
            super.update(*argsArray);
        }
    }
}

UpdateKeyFilter : UpdateFilter {
    var <>key;
    
    *new {
        |key|
        var func = "{ |obj, inKey| % == inKey }".format("\\" ++ key.asString).interpret;
        ^super.new(func).key_(key);
    }
    
    connectionTraceString {
        ^"%(%)".format(this.class, "\\" ++ key)
    }
}

DeferredUpdater : UpdateForwarder {
    classvar immediateDeferFunc, immediateDeferList;
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
    var <>connection, <>shouldFree;
    
    *new {
        |connection, shouldFree=false|
        ^super.new.connection_(connection).shouldFree_(shouldFree)
    }
    
    update {
        |object, what ...args|
        protect {
            super.update(object, what, *args);
        } {
            if (shouldFree) {
                connection.free();
                connection = nil;
            } {
                connection.disconnect();
            }
        }
    }
}

CollapsedUpdater : UpdateForwarder {
    var clock, force, delta, <collapseFunc;
    var deferredUpdate;
    var holdUpdates=false;
    
    *defaultCollapseFunc {
        ^{
            |nextUpdate|
            nextUpdate
        }
    }
    
    *new {
        |delta=0, clock=(AppClock), force=true|
        ^super.new.init(delta, clock, force).collapseFunc_(this.defaultCollapseFunc)
    }
    
    init {
        |inDelta, inClock, inForce|
        clock = inClock;
        force = inForce;
        delta = inDelta;
        CmdPeriod.add(this);
    }
    
    release {
        super.release();
        CmdPeriod.remove(this);
    }
    
    collapseFunc_{
        |func|
        collapseFunc = func;
    }
    
    deferIfNeeded {
        |func|
        if ((thisThread.clock == clock) && force.not) {
            func.value
        } {
            clock.sched(0, func);
        }
    }
    
    collapseUpdate_{
        |update|
        deferredUpdate = update;
    }
    
    update {
        |object, what ...args|
        var tmpdeferredUpdate;
        if (holdUpdates) {
            deferredUpdate = collapseFunc.value([object, what, args], deferredUpdate);
        } {
            holdUpdates = true;
            
            this.deferIfNeeded {
                super.update(object, what, *args);
            };
            
            clock.sched(delta, {
                holdUpdates = false;
                if (deferredUpdate.notNil) {
                    tmpdeferredUpdate = deferredUpdate;
                    deferredUpdate = nil;
                    this.update(tmpdeferredUpdate[0], tmpdeferredUpdate[1], *tmpdeferredUpdate[2]);
                };
            })
        }
    }
    
    doOnCmdPeriod {
        holdUpdates = false;
    }
}
