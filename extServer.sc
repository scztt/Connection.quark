ServerActionForwarder {
	classvar <added = false;

	*add {
		ServerBoot.add(this, \all);
		ServerTree.add(this, \all);
		ServerQuit.add(this, \all);
		added = true;
	}

	*remove {
		ServerBoot.remove(this, \all);
		ServerTree.remove(this, \all);
		ServerQuit.remove(this, \all);
		added = false;
	}

	*doOnServerBoot {
		|server|
		server.changed(\serverBoot);
	}

	*doOnServerQuit {
		|server|
		server.changed(\serverBoot);
	}

	*doOnServerTree {
		|server|
		server.changed(\serverBoot);
	}
}

+Server {
	signal {
		|symbolOrFunc|
		if ((symbolOrFunc == \serverBoot)
			|| (symbolOrFunc == \serverTree)
			|| (symbolOrFunc == \serverQuit))
		{
			if (ServerActionForwarder.added.not) {
				ServerActionForwarder.add;
			}
		};

		^super.signal(symbolOrFunc)
	}
}
