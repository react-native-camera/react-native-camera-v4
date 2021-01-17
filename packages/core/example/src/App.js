import * as React from 'react';
import { StyleSheet, View, Text } from 'react-native';
import Core from '@react-native-camera/core';
export default function App() {
    const [result, setResult] = React.useState();
    React.useEffect(() => {
        Core.multiply(3, 7).then(setResult);
    }, []);
    return (React.createElement(View, { style: styles.container },
        React.createElement(Text, null,
            "Result: ",
            result)));
}
const styles = StyleSheet.create({
    container: {
        flex: 1,
        alignItems: 'center',
        justifyContent: 'center',
    },
    box: {
        width: 60,
        height: 60,
        marginVertical: 20,
    },
});
//# sourceMappingURL=App.js.map