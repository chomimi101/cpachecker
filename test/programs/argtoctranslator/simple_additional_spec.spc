CONTROL AUTOMATON ARG

INITIAL STATE init;

STATE USEALL init:
    MATCH "[x > 0]" -> ASSUME {x<10} GOTO init;
    MATCH "[x > 0]" -> ASSUME {x>=10} ERROR;
    MATCH "[!(x > 0)]" -> ASSUME {x>-20} GOTO init;
    MATCH "[!(x > 0)]" -> ASSUME {x<=-20} ERROR;

END AUTOMATON
