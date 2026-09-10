package com.legalfam.backend.chat.application.policy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.legalfam.backend.chat.domain.exception.InvalidChatRequestException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * El mismo corpus que valida el patron del frontend (ChatInput.jsx). Los dos tienen que
 * coincidir: si uno bloquea y el otro no, el usuario recibe un rechazo del servidor despues de
 * que el cliente le dejara pasar.
 */
class ChatPrivacyPolicyTest {

    private final ChatPrivacyPolicy policy = new ChatPrivacyPolicy();

    @ParameterizedTest
    @ValueSource(strings = {
            "mi DNI es 45678912 y quiero pedir pension",
            "escribeme a juan@correo.com",
            "mi numero es 987654321",
            "llamame al 987 654 321",
            "mi cel +51 987 654 321",
            "contacto 51987654321",
            "mi celular es 999-888-777",
            "vivo en Av. Arequipa 1234",
            "mz. B lote 5, Comas",
            "calle Los Olivos 234",
            "jr. Puno 456",
            "mi fijo es 014451234",
            "llama al 01 445 1234",
            "mi telefono (01) 445 1234",
            "el fijo de la casa es 084 123456",
    })
    @DisplayName("rechaza el mensaje que trae un dato identificable")
    void rejectsPersonalData(String message) {
        assertThrows(InvalidChatRequestException.class, () -> policy.assertAllowed(message));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Edades y montos: el caso que la HU declara explicitamente que no se bloquea.
            "mi hija tiene 8 anos y su padre no aporta 500 soles al mes",
            "gano 2 500 soles y el aporta 800",
            "me deben 1 200 soles de 3 meses",
            // Fechas: lo que mas escribe quien cuenta una separacion.
            "nos separamos el 12.05.2024 y no hemos firmado nada",
            "la audiencia fue el 03-11-2023",
            "nos casamos el 15 de marzo de 2019",
            "la resolucion 05.2024 del juzgado",
            "la audiencia es a las 9 de la manana",
            // Numeros de expediente y de norma.
            "el expediente es 00123-2024-0-1801-JP-FC-01",
            "expediente 01234-2023-0-1801-JR-FC-05",
            "la Ley 30364 y el DL 1297 protegen a los menores",
            "el articulo 481 del Codigo Civil",
            // Palabras de direccion sin direccion detras.
            "el juzgado queda en la calle de al lado",
            "mi hijo no come manzana",
            "trabajo en la av. Arequipa y quiero pedir tenencia",
            // Consultas corrientes, incluida una en quechua con prestamos del castellano.
            "quiero saber como se calcula la pension de alimentos",
            "quiero pension de alimentos para mis 2 hijos",
            "Wawaypaq pension de alimentos DEMUNA-pi manakuyta munani",
    })
    @DisplayName("deja pasar la consulta que no trae ningun dato identificable")
    void allowsOrdinaryQueries(String message) {
        assertDoesNotThrow(() -> policy.assertAllowed(message));
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "   ", "hola" })
    @DisplayName("deja pasar el mensaje vacio o trivial")
    void allowsTrivialMessages(String message) {
        assertDoesNotThrow(() -> policy.assertAllowed(message));
    }
}
