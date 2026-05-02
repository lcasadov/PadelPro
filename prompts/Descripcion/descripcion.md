1. Objetivo

Quiero crear una aplicación para gestionar las reservas de una pista de padel en un pueblo en el que solo hay una pista.
Actualmente se hacen reservas por un grupo de Whatsapp, en la que una persona propone un partido a una hora y otros jugadores se van apuntando copiando y pegando hasta completar 4 jugadores, y por una aplicación (Fintonic). En Fintonic se realiza el pago de la reserva en el momento. En la reserva por Whatsapp se abre grupo a una hora y se paga en efectivo a la persona que lo gestiona. 
Problema: 1 a veces se producen solapes ya que lo que se reserva por Whatsapp no se registra en Fintonic hasta que la persona que lo gestiona lo reserva en la aplicación.
        2 No se lleva un registro electrónico del uso de la pista ni de los ingresos que esto produce.

Quiero crear una aplicación para gestinar las reservas de esta pista, los ingresos y los gastos. El alcance de la aplicación es el siguiente:
    1.- La herramienta debe tener una parte web que debe permitir el acceso a todos los usuarios dados de alta en la de la plataforma. La herramienta Web debe permitir realizar resevas, visualizar un calendario, pagar la reserva, visualizar un histórico de pagos y de reservas, etc...
    2.- La herramienta debe permitir interactuar con un grupo de Whatsapp y Telegram para la reserva de pistas, consultas de reservas, pago de reservas.
    

2. Funcionalidades de la aplicación
2.1. Módulo Web
Se debe generar una a aplicación Web que debe tener las siguientes funionalidades.
Menu con las siguientes opciones:
   - Dashboard - Donde se visualice un listado con las últimas reservas, un calendario con las reservas de hoy a una semana en adelante y gráficas de uso de la pista semanal, mensual y anual.
   - Calendario para visualizar todas las reservas de la pista. 
   - Perfil de cada usuario (se visualiza por cada usuario con sus datos)
    * Nueva Reserva: Se permitirá la realización de una nueva reserva mostrando un calendario con las horas de cada día en el que se mostrarán las horas ocupadas. Solo se podrá reservar en      horas libres. La reserva se realizará por un mínimo de una hora de duración sumando intervalos de media hora hasta un máximo de 3 horas. 
        A la reserva se pueden añadir nombres de los participantes en esa reserva, que no tienen porque ser usuarios de la aplicación, hasta completar 4 participantes. 
        Al formalizar la reserva se debe generar un mensaje en el grupo de Whatsapp/Telegram como se muestra a continuación:
            HOY 18:30-20:00
            🎾 Alejandro
            🎾 Luis
            🎾 Marcos
            🎾
        Si no se rellenan todos los huecos, se podrán apuntar participantes a esa reserva de dos formas:
        - Contestando al Whatsapp/Telegram copiado el Whatsapp y añadiendo el nombre en el hueco. Esto lo leerá la aplicación y completará la reserva con el participante nuevo.
             HOY 18:30-20:00
            🎾 Alejandro
            🎾 Luis
            🎾 Marcos
            🎾 Juan
        - Desde la Web en el apartado incluirse en una reserva.
        La reserva se debe poder cancelar en cualquier momento, con un periodo de antelación configurado por el administrador. A partir de ese momento, se considerará como reserva pendiente de pago.
    * Incluirse en una reserva. Cuando el usuario acceda, debe mostrar un calendario de una semana con las reservas que no esten completas y requieran de un participante. El usuario seleccionará la que desee y se apuntará. Se debe generar un mensaje en el grupo de Whatsapp/Telegram como se mostraba anteriormente con su nombre.
    * Pago de reserva: El usuario que realiza la reserva es el que debe pagarla. Por tanto cuando un usuario realiza la reserva, aunque no esté completa, será el usuario que reserva al que se le adjudique el pago. Si se cancelara la reserva se cancelaría el pago. El pago se debe permitir mediante un link para el pago electrónico o en efectivo al administrador.
    * Histórico Reservas realizadas, pagadas y pendientes de pago. Se debe mostrar un histórico consultando por fechas de las reservas resalizadas por el usuario, las pagadas y las pendientes de pago.
   - Administración - solo para usuarios administradores.
    * Usuarios: Se debe visualizar una tabla con los usuarios creadas hasta el momento.
        La tabla debe mostrar las columnas de login, nombre y apellidos,teléfono, email, estado, Fecha de registro, rol. A la derecha aparece un icono para borrar el usuario. El borrado es borrado cambiando el estado de activo a no activo.
        El estado es activo o no activo. El usuario no activo no podrá hacer nada en la plataforma.
  
        Al crear un usuario se le debe mandar un correo con su user_login y password a su email y su whatsapp
        Se debe poder resetear la password de un usuario. Esto generará una nueva password y se mandará un mail al usuario.
        El usuario debe poder resetear su password en caso de olvido desde la pagina de login. Se enviará por seguridad un código al whatsapp y otro a su mail y si los introduce de forma correcta se le resetea la password recibiendo la pass en el correo electrónico o en Whatsapp.
  
        Roles y permisos : Los usuarios tendrán 2 roles principales con responsabilidades claramente diferenciadas:
            - Administrador (Administrador de la aplicación que puede hacer todo).
            - Usuario. Solo puede hacer lo relativo a su usuario
    * Notificaciones. Se deben poder generar notificaciones a los usuarios por mail y WhatsApp/Telegram y notificaciones al grupo de WhatsApp/Telegram.
    * Administración de Reservas. Se debe poder configurar datos como tiempo de anterioridad para cancelación en horas, link para el pago electrónico. En este apartado el administrador debe poder pone una reserva como pagada si el pago se ha realizado en efectivo.

3. Stack tecnológico

El stack tecnológico sera el siguiente:

- Backend: Java  `Spring Boot` con Java 21.
- Frontend: `React JS`
- BBDD: `Postgres SQL`
- La aplicación debe desplegarse en contenedores docker.
- Alto nivel de seguridad y auditabilidad.
- Se debe integrar con GitHub Projects + Copilot para toda la gestión de tareas y sprints.
- Se debe utilizar OpenSpecs para la gestión de las Specs.
- La auntenticación se realiza en la web y la password debe cifrarse para que no se pueda ver en claro. 
- Se debe registrar las acciones realizadas por cada usuario para auditoria.

4. Pruebas y Calidad de Código

La plataforma debe implementar una estrategia integral de testing basada en metodologías TDD (Test-Driven Development) y BDD (Behavior-Driven Development), garantizando una cobertura mínima del 80% en todo el código base.

Los Objetivos de Calidad

- Cobertura de código: Mínimo 80% (líneas) y 75% (ramas)
- Pirámide de testing: 80% tests unitarios, 15% integración, 5% E2E
- Metodologías: TDD para desarrollo, BDD para especificación de requisitos
- Automatización: Integración completa en pipelines CI/CD 

Tipos de Pruebas

Backend (Spring Boot)
- Tests unitarios: JUnit 5, Mockito, H2 Database (en memoria)
- Tests de integración: Spring Boot Test
- Cobertura: JaCoCo con umbrales mínimos: 80% líneas, 75% ramas
- Seguridad: Spring Security Test para validación RBAC

Frontend (React + Vite)
- Tests unitarios: Jest, React Testing Library
- Tests de integración: MSW (Mock Service Worker)
- Tests E2E: Cypress para flujos críticos de usuario
- Cobertura: Jest con umbrales mínimos: 80% líneas, 75% ramas

Base de Datos (Estrategia Híbrida)
- H2 Database: Tests unitarios rápidos con modo `MODE=Postgres SQL`
- Postgres SQL: Tests de integración con fidelidad de producción 
- Justificación: Velocidad en desarrollo (H2) + validación realista (Postgres SQL)

5. Flujos Críticos (Cobertura 100% Obligatoria)

1. Reservas por Whatsapp: Cuando un usuario en grupo solicita reserva de pista a una hora, esta debe estar libre. El usuario debe poner en el whatsapp reserva de pista, fecha (dd/mm/aa), hora (hh:mm) y duración.Se le pedirá un código que se habrá mandado a su Whatsapp personal para confirmar la reserva

2. Cancelación por Whatsapp: Cuando un usuario en grupo solicita la cancelación de una reserva por Whatsapp, debe poner en el whatsapp cancelación reserva de pista, fecha (dd/mm/aa), hora (hh:mm). Se le pedirá un código que se habrá mandado al Whatsapp personal de la persona que realizó la reserva para cancelar la reserva. 
3. Pago: El pago de la reserva se puede realizar en cualquier momento. El pago se puede realizar al administrador en efectivo, en cuyo caso este administrador pondrá la reserva como pagada, o se mandará un link de pago al whatsapp de la persona que ha realizado la reserva. Este link de pago será un link seguro, con el banco configurado para tal efecto.