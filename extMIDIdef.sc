+MIDIFunc {
	*toCC14Value {
		|value|
		var msvalue = value >> 7;
		^[msvalue, value - (msvalue << 7)]
	}

	*fromCC14Value {
		|msValue, lsValue=0|
		^((msValue << 7) + lsValue)
	}

	*makeCC14WrapFunc {
		|func, ccNum, lsbFirst=false|
		var firstValue=0;
		^{
			|value, num, chan, src|
			var isFirstValue = (num == ccNum).xor(lsbFirst);
			// [isFirstValue, value, num, chan, src].postln;

			if (isFirstValue) {
				firstValue = value;
			} {
				if (lsbFirst) {
					value = MIDIFunc.fromCC14Value(value, firstValue);
				} {
					value = MIDIFunc.fromCC14Value(firstValue, value);
				};
				firstValue = 0;
				func.value(value, num, chan, src)
			}
		}
	}

	*cc14 { arg func, ccNum, chan, srcID, argTemplate, dispatcher, ccInterval=32, lsbFirst=false;
		if (ccNum.isArray) { Error("Arrayed ccNum for cc14 not supported").throw };

		^this.new(
			this.makeCC14WrapFunc(func, ccNum, lsbFirst),
			[ccNum, ccNum + ccInterval].flatten, chan, \control, srcID, argTemplate, dispatcher);
	}
}

+MIDIdef {
	*cc14 { arg key, func, ccNum, chan, srcID, argTemplate, dispatcher, ccInterval=32, lsbFirst=false;
		var wrapFunc, accumulatedValue;

		^this.new(key,
			this.makeCC14WrapFunc(func, ccNum, lsbFirst),
			[ccNum, ccNum + ccInterval].flatten, chan, \control, srcID, argTemplate, dispatcher);
	}
}

+MIDIOut {
	control14 {
		|chan, ctlNum=7, val=8192, ccInterval=32, lsbFirst=false|
		var values;

		ctlNum = ctlNum.asInteger;
		values = MIDIFunc.toCC14Value(val);

		if (lsbFirst) {
			this.write(3, 16rB0, chan.asInteger, ctlNum, values[1]);
			this.write(3, 16rB0, chan.asInteger, ctlNum + ccInterval, values[0]);
		} {
			this.write(3, 16rB0, chan.asInteger, ctlNum, values[0]);
			this.write(3, 16rB0, chan.asInteger, ctlNum + ccInterval, values[1]);
		};
	}
}
