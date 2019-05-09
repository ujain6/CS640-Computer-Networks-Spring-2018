JC = javac
.SUFFIXES: .java .class
.java.class:
	$(JC) $*.java

CLASSES = \
        Receiver.java \
        Sender.java \
        TCPend.java  

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) *.class
