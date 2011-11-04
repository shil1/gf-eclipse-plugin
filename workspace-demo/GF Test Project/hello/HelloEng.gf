--concrete HelloEng of HelloAbs = ResEng ** { 
concrete HelloEng of HelloAbs = open (Alias = ResEng) in {
	
	flags
		coding = utf8 ;
 
	lincat
		Greeting, Farewell = {s : Str} ;
		Recipient = {s : Alias.Gender => Str} ;

	lin
 		Hello recip = {s = "hello" ++ recip.s ! Masc} ;
		Goodbye recip = {s = "goodbye" ++ recip.s ! Alias.Fem} ;
		
		World = {s = \\_ => "world"} ;
		Parent = { s = table {
			Alias.Masc => "dad" ; Alias.Fem => "mum"
		} } ;
		Friends = mega "friends" "loved ones" ;

	oper
		superate : Str -> Recipient = \s ->
			lin Recipient { s = \\_ => "super" ++ s } ;
 
		mega : Recipient = overload {
			mega : Str -> Recipient = \s ->
				lin Recipient { s = \\_ => "mega" ++ s } ;
				
			mega : Str -> Str -> Recipient = \s,r ->
				lin Recipient { s = \\_ => "mega" ++ s ++ "and" ++ "mega" ++ r }
		};
 
} 
