+Dictionary {
	atCreate {
		|key, defaultFunc|
		var val, newVal;

		val = this.at(key);
		if (val.notNil) {
			^val
		} {
			newVal = defaultFunc.value();
			this.put(key, newVal);
			^newVal;
		}
	}
}

+Association {
	connect {
		|...otherAssocs|
		^([this] ++ otherAssocs).collectAs({
			|assoc|
			assoc.key.connectTo(assoc.value)
		}, ConnectionList)
	}
}