+Dictionary {
	atCreate {
		|key, defaultFunc|
		var val = this.at(key);
		if (val.notNil) {
			^val
		} {
			var newVal = defaultFunc.value();
			this.put(key, newVal);
			^newVal;
		}
	}
}
