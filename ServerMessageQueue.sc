ServerMessageQueue {
	var server;
	var <synced = true;
	var <bundles, <condition;
	var <>latency;

	*new {
		|server|
		^super.new.init(server)
	}

	init {
		|inServer|

		server = inServer;
		bundles = List(64);
		latency = server.latency;
		ServerQuit.add(this);
	}

	doOnServerQuit {
		synced = true;
		condition = nil;
		bundles.clear;
	}

	processBundles {
		var bundle;

		if (condition.isNil) {
			while { bundles.notEmpty } {
				bundle = bundles.removeAt(0);

				if (bundle == \sync) {
					condition = Condition(false);
					server.sendBundle(latency, ["/sync", server.addr.makeSyncResponder(condition)]);
					fork {
						condition.wait();
						condition = nil;
						this.processBundles();
					};
					^false;
				} {
					server.listSendBundle(nil, bundle);
				}
			}
		}
	}

	makeBundle {
		|func|
		bundles.add(server.makeBundle(false, func));
		this.processBundles()
	}

	makeBundleSync {
		|func|
		bundles.add(server.makeBundle(false, func));
		bundles.add(\sync);
		this.processBundles();
	}

	sync {

	}
}
