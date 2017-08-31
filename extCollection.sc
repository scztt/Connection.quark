+SequenceableCollection {
	connectAll {
		|...dependants|
		^ConnectionList.newFrom(
			this.collect(_.connectTo(*dependants))
		)
	}

	connectEach {
		|signalNameOrFunc, dependantList, methodNameOrFunc|
		var tempVal;

		if (this.size != dependantList.size) {
			Error("connectEach requires collections of equal size (this.size = %, other.size = %)".format(this.size, dependantList.size)).throw;
		};

		if (signalNameOrFunc.notNil && signalNameOrFunc.isKindOf(Function).not) {
			tempVal = signalNameOrFunc;
			signalNameOrFunc = { |o| o.signal(tempVal) };
		};

		if (methodNameOrFunc.notNil && methodNameOrFunc.isKindOf(Function).not) {
			tempVal = methodNameOrFunc;
			methodNameOrFunc = { |o| o.methodSlot(tempVal) };
		};

		^this.collectAs({
			|object, i|
			var dependant = dependantList[i];

			if (methodNameOrFunc.notNil) {
				dependant = methodNameOrFunc.value(dependant);
			};

			if (signalNameOrFunc.notNil) {
				object = signalNameOrFunc.value(object);
			};

			object.connectTo(dependant);
		}, ConnectionList)
	}

	eachMethodSlot {
		|method|
		^this.collect(_.methodSlot(method));
	}

	eachValueSlot {
		|setter|
		^this.collect(_.valueSlot(setter));
	}

	eachInputSlot {
		^this.collect(_.inputSlot());
	}

	eachArgSlot {
		|argName|
		^this.collect(_.argSlot(argName));
	}

	signalsDo {
		|signal, func|
		this.collect(_.signal(signal)).do(func);
	}

	signalsCollect {
		|signal, func|
		func = func ? {|s|s}; // IMPORTANT: we want the function, not the evaluated result
		^this.collect(_.signal(signal)).collect(func);
	}
}