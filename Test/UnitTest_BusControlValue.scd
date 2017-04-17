/*
UnitTest.reset; UnitTestScript("ControlValue", thisProcess.nowExecutingPath).runScript;
*/

{
	|test|
	var object, connection;
	var recordFunc = {
		|object, changed ...args|
		~object = object;
		~changed = changed;
		~args = args;
	};
	var collectFunc = {
		|object, changed ...args|
		~object = ~object.add(object);
		~changed = ~changed.add(changed);
		~args = ~args.add(args);
	};
	var lastMessageEquals = {
		|object, changed, args, description|
		test.assertEquals(object, ~object, description ++ " (object correct)");
		test.assertEquals(changed, ~changed, description ++ " (changed correct)");
		test.assertArrayEquals(args, ~args, description ++ " (args correct)");
	};
	var clear = { ~object = ~changed = ~args = nil };
	test.addUniqueMethod(\assertArrayEquals, {
		|test, a, b, message|
		test.assert(
			((a.size + b.size) == 0) or: {
				(a.size == b.size) and: {
					a.every { |item, i| item == b[i] } }
			}, message ++ " (% == %".format(a, b));
	});




	////////////////////////////////////////////////////////////
	// NumericControlValue
	test.assertEquals(BusControlValue(0).value, 0);
	test.assertEquals(BusControlValue(100).value, 1);
	test.assertEquals(BusControlValue(0, ControlSpec(1, 10)).value, 1);
	test.assertEquals(BusControlValue(100, ControlSpec(1, 10)).value, 10);

}
