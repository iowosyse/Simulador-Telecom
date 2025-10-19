# Simulador-Telecom
El simulador busca imitar como se realizan las telecomunicaciones en la vida real; tiene un protocolo de HANDSHAKE en el que se envía un header que indica la cantidad de paquetes que se envían, se envían los paquetes por medio de una ventana deslizante y hasta que no se haya recibido confirmación de todos los paquetes de la ventana no se envía otro batch de paquetes.

La GUI está hecha con JavaFX, con el sdk de ```liberica-full-21```.